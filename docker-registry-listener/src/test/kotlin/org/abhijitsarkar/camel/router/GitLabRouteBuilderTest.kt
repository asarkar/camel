package org.abhijitsarkar.router

import org.abhijitsarkar.model.Group
import org.abhijitsarkar.model.Project
import org.apache.camel.EndpointInject
import org.apache.camel.ProducerTemplate
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.test.spring.CamelSpringBootRunner
import org.apache.camel.test.spring.DisableJmx
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles


/**
 * @author Abhijit Sarkar
 */
@RunWith(CamelSpringBootRunner::class)
@SpringBootTest(classes = arrayOf(GitLabRouteBuilderTest::class),
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("it")
@DisableJmx
// needed because we don't have a SpringBootApplication
@EnableAutoConfiguration
@ComponentScan(basePackageClasses = arrayOf(GitLabRouteBuilder::class))
class GitLabRouteBuilderTest {
    @EndpointInject(uri = "mock:groupConsumerEndpoint")
    lateinit var groupConsumerEndpoint: MockEndpoint

    @EndpointInject(uri = "mock:projectConsumerEndpoint")
    lateinit var projectConsumerEndpoint: MockEndpoint

    @Autowired
    lateinit var producerTemplate: ProducerTemplate

    @Test
    fun testGroupConsumerRoute() {
        groupConsumerEndpoint.expectedMessageCount(1)
        producerTemplate.sendBody("direct:eventListenerEndpoint", "whatever")
        groupConsumerEndpoint.assertIsSatisfied(2000L)
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