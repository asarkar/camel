package org.abhijitsarkar.camel.router

import org.abhijitsarkar.camel.Application
import org.abhijitsarkar.camel.model.Envelope
import org.abhijitsarkar.camel.model.Event
import org.apache.camel.Exchange
import org.apache.camel.ExchangePattern
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestBindingMode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

/**
 * @author Abhijit Sarkar
 */
@Component
class EventListenerRouteBuilder : RouteBuilder() {
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
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)

                .route()
                .setExchangePattern(ExchangePattern.InOnly)
                .to("log:${javaClass.name}?level=DEBUG&showException=true&showHeaders=true&showOut=true" +
                        "&showStackTrace=true&marker=eventListenerRoute")
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
                    e.out.setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.ACCEPTED.value())
                }
                .endRest()

                .get("/{eventId}")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .route()
                .process { e ->
                    val query = findByIdTemplate
                            .replace("\n", " ")
                            .replace("{eventId}", "${e.`in`.getHeader("eventId").toString()}")
                    e.`in`.body = query
                }
                .to("{{queryAuditingEndpoint:log:foo?level=OFF}}")

        val numCores = Runtime.getRuntime().availableProcessors()
        from("seda:eventAuditingEndpoint?waitForTaskToComplete=Never&purgeWhenStopping=true&concurrentConsumers=$numCores")
                .to("{{eventAuditingEndpoint:log:foo?level=OFF}}")
    }
}