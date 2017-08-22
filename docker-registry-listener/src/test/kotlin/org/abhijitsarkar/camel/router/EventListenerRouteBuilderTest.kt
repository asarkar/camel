package org.abhijitsarkar.camel.router

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.kittinunf.fuel.httpPost
import org.abhijitsarkar.camel.Application
import org.abhijitsarkar.camel.model.Actor
import org.abhijitsarkar.camel.model.Envelope
import org.abhijitsarkar.camel.model.Event
import org.abhijitsarkar.camel.model.Target
import org.apache.camel.EndpointInject
import org.apache.camel.Predicate
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.test.spring.CamelSpringBootRunner
import org.apache.camel.test.spring.DisableJmx
import org.apache.http.HttpHeaders
import org.apache.http.HttpStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * @author Abhijit Sarkar
 */
@RunWith(CamelSpringBootRunner::class)
@SpringBootTest(classes = arrayOf(Application::class),
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("it")
@DisableJmx
class EventListenerRouteBuilderTest {
    @EndpointInject(uri = "mock:eventConsumerEndpoint")
    lateinit var eventConsumerEndpoint: MockEndpoint

    @Test
    fun testEventListenerRoute() {
        val data = listOf(
                Triple("push", "latest", 0),
                Triple("push", "whatever", 1),
                Triple("pull", "latest", 0),
                Triple("pull", "whatever", 0)
        )
        val mapper = ObjectMapper().registerModule(KotlinModule())

        data.forEach { (action, tag, messageCount) ->
            val envelope = Envelope(listOf(Event(
                    "1",
                    "whatever",
                    action,
                    Target("repo", tag),
                    Actor("whoever")
            )))
            if (messageCount > 0) {
                eventConsumerEndpoint.expectedMessagesMatches(Predicate { e ->
                    val events = e.`in`.getHeader(Application.DOCKER_REGISTRY_EVENTS_HEADER,
                            List::class.java) as List<Event>
                    events?.first()?.id == "1"
                })
            } else {
                eventConsumerEndpoint.expectedMessageCount(messageCount)
            }

            val (_, response, _) = "http://localhost:8080/events"
                    .httpPost()
                    .header(
                            HttpHeaders.CONTENT_TYPE to Application.APPLICATION_JSON_MEDIA_TYPE,
                            HttpHeaders.ACCEPT to Application.APPLICATION_JSON_MEDIA_TYPE
                    )
                    .body(mapper.writeValueAsString(envelope))
                    .response()

            val events = mapper.readValue<List<String>>(response.data, object : TypeReference<List<String>>() {})

            assert(events.first() == "1", { "Expected body to contain event ids." })
            assert(response.httpStatusCode == HttpStatus.SC_ACCEPTED, { "Expected status code: ACCEPTED." })

            eventConsumerEndpoint.assertIsSatisfied(2000L)
            eventConsumerEndpoint.reset()
        }
    }
}