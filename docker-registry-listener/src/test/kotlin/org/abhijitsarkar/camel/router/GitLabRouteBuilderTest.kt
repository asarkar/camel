package org.abhijitsarkar.camel.router

import org.abhijitsarkar.camel.Application
import org.abhijitsarkar.camel.model.Group
import org.abhijitsarkar.camel.model.Project
import org.apache.camel.CamelContext
import org.apache.camel.EndpointInject
import org.apache.camel.ProducerTemplate
import org.apache.camel.builder.NotifyBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.test.spring.CamelSpringBootRunner
import org.apache.camel.test.spring.DisableJmx
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit


/**
 * @author Abhijit Sarkar
 */
@RunWith(CamelSpringBootRunner::class)
@SpringBootTest(classes = arrayOf(Application::class),
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("it")
@DisableJmx
class GitLabRouteBuilderTest {
    @EndpointInject(uri = "mock:groupConsumerEndpoint")
    lateinit var groupConsumerEndpoint: MockEndpoint

    @EndpointInject(uri = "mock:projectConsumerEndpoint")
    lateinit var projectConsumerEndpoint: MockEndpoint

    @Autowired
    lateinit var producerTemplate: ProducerTemplate

    @Autowired
    lateinit var context: CamelContext

    @Test
    fun testGroupConsumerRoute() {
        val notifyBuilder = NotifyBuilder(context)
                .wereSentTo(groupConsumerEndpoint.endpointUri)
                .whenDone(1)
                .create()
        groupConsumerEndpoint.expectedMessageCount(1)

        producerTemplate.sendBody("seda:eventConsumerEndpoint", "whatever")

        val done = notifyBuilder.matches(5, TimeUnit.SECONDS)
        assert(done, { "Should be done." })
        groupConsumerEndpoint.assertIsSatisfied(5000L)
    }

    @Test
    fun testProjectConsumerRoute() {
        projectConsumerEndpoint.expectedMessageCount(2)

        val project1 = Project("test1")
                .apply { sshUrl = "git@gitlab.com:abhijitsarkar.org/test1.git" }
        val project2 = Project("test2")
                .apply { sshUrl = "git@gitlab.com:abhijitsarkar.org/test2.git" }

        val group = Group("abhijitsarkar.org", listOf(project1, project2))
        producerTemplate.sendBody("direct:groupConsumerEndpoint", group)
        groupConsumerEndpoint.assertIsSatisfied(5000L)
    }
}