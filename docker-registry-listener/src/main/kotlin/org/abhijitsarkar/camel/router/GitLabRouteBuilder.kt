package org.abhijitsarkar.camel.router

import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.abhijitsarkar.camel.model.Group
import org.abhijitsarkar.camel.translator.JGitAgent
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.dataformat.JsonDataFormat
import org.apache.camel.model.dataformat.JsonLibrary
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component


/**
 * @author Abhijit Sarkar
 */
@Component
class GitLabRouteBuilder(val jGitAgent: JGitAgent) : RouterUtil, RouteBuilder() {
    override fun configure() {
        val dataFormat = JsonDataFormat(JsonLibrary.Jackson).apply {
            moduleClassNames = KotlinModule::class.java.name
            unmarshalType = Group::class.java
        }

        val logUri = super.logUri(javaClass)

        from("seda:eventConsumerEndpoint?$sedaOptions")
                .id("groupConsumerRoute")
                .marshal()
                .json(JsonLibrary.Jackson)
                .setHeader(HttpHeaders.ACCEPT, constant(MediaType.APPLICATION_JSON_VALUE))
                .setHeader("PRIVATE-TOKEN", constant("{{gitlab.privateToken:N/A}}"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethod.GET.name))
                .to("https4://{{gitlab.baseUri:http://locahost:8080}}/api/v4/groups/" +
                        "{{gitlab.groupName::N/A}}?bridgeEndpoint=true")
                .unmarshal(dataFormat)
                .multicast()
                .parallelProcessing()
                .to("$logUri&marker=groupConsumerRoute",
                        "{{gitlab.groupConsumerEndpoint:log:foo?level=OFF}}")

        from("direct:groupConsumerEndpoint")
                .id("projectConsumerRoute")
                .split(simple("\${body.projects}"))
                .parallelProcessing()
                .bean(jGitAgent, "clone")
//                .process(object : AsyncProcessor {
//                    // http://camel.apache.org/asynchronous-processing.html
//                    override fun process(e: Exchange, callback: AsyncCallback): Boolean {
//                        executorService.submit {
//                            e.`in`.body = jGitAgent.clone(e.`in`.getBody(Project::class.java))
//                            callback.done(false)
//                        }
//                        return false
//                    }
//
//                    override fun process(p0: Exchange?) {
//                        TODO("not implemented")
//                    }
//                })
                .multicast()
                .parallelProcessing()
                .to("$logUri&marker=projectConsumerRoute",
                        "{{gitlab.projectConsumerEndpoint:log:foo?level=OFF}}")

        from("direct:projectConsumerEndpoint")
                .id("updateRoute")
                .bean(jGitAgent, "update")
                .filter(simple("\${body.isEmpty} == false"))
                .multicast()
                .parallelProcessing()
                .to("$logUri&marker=updateRoute", "seda:updateAuditingEndpoint")

        from("seda:updateAuditingEndpoint?$sedaOptions")
                .id("updateAuditingRoute")
                .to("{{updateAuditingEndpoint:log:foo?level=OFF}}")
    }
}