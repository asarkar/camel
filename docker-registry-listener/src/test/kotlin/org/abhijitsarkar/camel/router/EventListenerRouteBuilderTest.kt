package org.abhijitsarkar.camel.router

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
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

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

    val restTemplate = TestRestTemplate(RestTemplate())

    @Test
    fun testEventListenerRoute() {
        val data = listOf(
                Triple("push", "latest", 0),
                Triple("push", "whatever", 1),
                Triple("pull", "latest", 0),
                Triple("pull", "whatever", 0)
        )

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
            }

            restTemplate.postForObject("http://localhost:8080/events", envelope, String::class.java)
            eventConsumerEndpoint.assertIsSatisfied(2000L)
            eventConsumerEndpoint.reset()
        }
    }
}