package org.abhijitsarkar.camel.router

import org.abhijitsarkar.camel.Application
import org.abhijitsarkar.camel.model.Envelope
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestBindingMode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * @author Abhijit Sarkar
 */
@Component
class EventListenerRouteBuilder : RouteBuilder() {
    @Value("\${eventConsumerEndpoint}")
    lateinit var eventConsumerEndpoint: String

    override fun configure() {
        val endpoints = eventConsumerEndpoint
                .split(",")
                .map(String::trim)
                .toTypedArray()

        restConfiguration()
                .component("netty4-http")
                .host("localhost")
                .port(8080)
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("json.in.moduleClassNames", "com.fasterxml.jackson.module.kotlin.KotlinModule")

        rest()
                .id("eventListenerRoute")
                .post("/events").type(Envelope::class.java)
                .consumes("application/json")
                .route()
                .to("log:${javaClass.name}?level=DEBUG")
                .filter()
                .body(Envelope::class.java, { (events) ->
                    events.any { it.action == "push" && it.target.tag != "latest" }
                })
                .setHeader(Application.DOCKER_REGISTRY_EVENTS_HEADER, simple("\${body.events}"))
                .multicast()
                .to(*endpoints)
    }
}