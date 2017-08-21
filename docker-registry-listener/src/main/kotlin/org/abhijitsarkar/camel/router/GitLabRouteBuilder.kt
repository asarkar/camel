package org.abhijitsarkar.camel.router

import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.abhijitsarkar.camel.model.Group
import org.abhijitsarkar.camel.translator.JGitAgent
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.dataformat.JsonDataFormat
import org.apache.camel.model.dataformat.JsonLibrary
import org.springframework.stereotype.Component

/**
 * @author Abhijit Sarkar
 */
@Component
class GitLabRouteBuilder(val jGitAgent: JGitAgent) : RouteBuilder() {
    override fun configure() {
        val dataFormat = JsonDataFormat(JsonLibrary.Jackson).apply {
            moduleClassNames = KotlinModule::class.java.name
            unmarshalType = Group::class.java
        }

        from("direct:eventConsumerEndpoint")
                .id("groupConsumerRoute")
                .marshal()
                .json(JsonLibrary.Jackson)
                .setHeader("Accept", constant("application/json"))
                .setHeader("PRIVATE-TOKEN", constant("{{gitlab.privateToken}}"))
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .to("https4://{{gitlab.baseUri}}/api/v4/groups/{{gitlab.groupName}}?bridgeEndpoint=true")
                .unmarshal(dataFormat)
                .to("log:${javaClass.name}?level=DEBUG")
                .to("{{gitlab.groupConsumerEndpoint}}")

        from("direct:groupConsumerEndpoint")
                .id("projectConsumerRoute")
                .split(simple("\${body.projects}"))
                .bean(jGitAgent, "clone")
                .to("log:${javaClass.name}?level=DEBUG")
                .to("{{gitlab.projectConsumerEndpoint}}")

        from("direct:projectConsumerEndpoint")
                .id("updateRoute")
                .bean(jGitAgent, "update")
                .filter(simple("\${body.toString().trim()} != ''"))
                .to("log:${javaClass.name}?level=DEBUG")
                .to("{{auditingEndpoint}}")
    }
}