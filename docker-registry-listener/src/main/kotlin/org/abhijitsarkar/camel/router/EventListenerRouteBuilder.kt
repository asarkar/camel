package org.abhijitsarkar.camel.router

import org.abhijitsarkar.camel.Application
import org.abhijitsarkar.camel.model.Envelope
import org.abhijitsarkar.camel.model.Event
import org.apache.camel.Exchange
import org.apache.camel.ExchangePattern
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestBindingMode
import org.apache.http.HttpStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * @author Abhijit Sarkar
 */
@Component
class EventListenerRouteBuilder : RouterUtil, RouteBuilder() {
    @Value("\${findByIdTemplate:}")
    lateinit var findByIdTemplate: String

    override fun configure() {
        restConfiguration()
                .component("netty4-http")
                .host("localhost")
                .port(8080)
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("json.in.moduleClassNames", "com.fasterxml.jackson.module.kotlin.KotlinModule")

        rest("/events")
                .id("eventListenerRoute")
                .post()
                .type(Envelope::class.java)
                .outType(List::class.java)
                .consumes(Application.APPLICATION_JSON_MEDIA_TYPE)
                .produces(Application.APPLICATION_JSON_MEDIA_TYPE)

                .route()
                .setExchangePattern(ExchangePattern.InOnly)
                .to("log:${super.logUri(javaClass)}")
                .setHeader(Application.DOCKER_REGISTRY_EVENTS_HEADER, simple("\${body.events}"))

                .choice()
                .`when`()
                .body(Envelope::class.java, { (events) ->
                    events.any { it.action == "push" && it.target.tag != "latest" }
                })
                .multicast()
                .parallelProcessing()
                .to("{{eventConsumerEndpoint:log:foo?level=OFF}}", "seda:eventAuditingEndpoint")
                .endChoice()
                .otherwise()
                .end()

                .process { e ->
                    @Suppress("UNCHECKED_CAST")
                    val events = e.`in`.getHeader(Application.DOCKER_REGISTRY_EVENTS_HEADER, List::class.java) as List<Event>
                    e.out.body = events.map(Event::id)
                    e.out.setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_ACCEPTED)
                }
                .endRest()

                .get("/{eventId}")
                .produces(Application.APPLICATION_JSON_MEDIA_TYPE)
                .route()
                .process { e ->
                    val query = findByIdTemplate
                            .replace("[\\s\\n]+".toRegex(), " ")
                            .replace("{eventId}", "${e.`in`.getHeader("eventId").toString()}")
                    e.`in`.body = query
                }
                .to("{{queryAuditingEndpoint:log:foo?level=OFF}}")

        from("seda:eventAuditingEndpoint?$sedaOptions")
                .to("{{eventAuditingEndpoint:log:foo?level=OFF}}")
    }
}