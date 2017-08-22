package org.abhijitsarkar.camel

import org.abhijitsarkar.camel.model.Actor
import org.abhijitsarkar.camel.model.Envelope
import org.abhijitsarkar.camel.model.Event
import org.abhijitsarkar.camel.model.Target
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Random
import java.util.UUID


/**
 * @author Abhijit Sarkar
 */
class TestApplication {
}

fun main(args: Array<String>) {
    val majorVersion = Random()
            .ints(1, 1, 9999)
            .findFirst()
            .asInt
    val event = Event(
            UUID.randomUUID().toString(),
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "push",
            Target("library/mysql", "$majorVersion.0.0"),
            Actor("whoever")
    )
    val envelope = Envelope(listOf(event))

    println("Envelope: $envelope")

    val restTemplate = RestTemplate()
    val requestEntity = HttpEntity<Envelope>(envelope, null)
    val postResponse = restTemplate.exchange(
            "http://localhost:8080/events",
            HttpMethod.POST,
            requestEntity,
            object : ParameterizedTypeReference<List<String>>() {}
    )
    println("POST status: ${postResponse.statusCode}")
    println("POST body: ${postResponse.body}")

    for (i in 10 downTo 1) {
        if (i > 0) {
            println("${String.format("%1\$2s", i)}...")
            Thread.sleep(1000L)
        }
    }

    val getResponse = restTemplate.getForEntity(
            "http://localhost:8080/events/${postResponse.body.first()}",
            String::class.java)
    println("GET status: ${getResponse.statusCode}")
    println("GET body: ${getResponse.body}")
}