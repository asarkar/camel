package org.abhijitsarkar.router

import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.abhijitsarkar.model.Group
import org.abhijitsarkar.translator.JGitAgent
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
//                .marshal()
//                .json(JsonLibrary.Jackson)
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
//                .split(JsonPathExpression("\$.projects[*].ssh_url_to_repo"))
                .bean(jGitAgent, "clone")
                .to("{{gitlab.projectConsumerEndpoint}}")

        from("direct:projectConsumerEndpoint")
                .id("updateRoute")
                .bean(jGitAgent, "update")
                .to("{{auditingEndpoint}}")
    }
}