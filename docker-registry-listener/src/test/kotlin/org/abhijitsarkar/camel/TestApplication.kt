package org.abhijitsarkar.camel

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import org.abhijitsarkar.camel.model.Actor
import org.abhijitsarkar.camel.model.Envelope
import org.abhijitsarkar.camel.model.Event
import org.abhijitsarkar.camel.model.Target
import org.apache.http.HttpHeaders
import org.springframework.boot.test.web.client.TestRestTemplate
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

    val mapper = ObjectMapper().registerModule(KotlinModule())
    val (_, postResponse, _) = "http://localhost:8080/events"
            .httpPost()
            .header(
                    HttpHeaders.CONTENT_TYPE to Application.APPLICATION_JSON_MEDIA_TYPE,
                    HttpHeaders.ACCEPT to Application.APPLICATION_JSON_MEDIA_TYPE
            )
            .body(mapper.writeValueAsString(envelope))
            .response()

    val events = mapper.readValue<List<String>>(postResponse.data, object : TypeReference<List<String>>() {})

    println("POST status: ${postResponse.httpStatusCode}")
    println("POST body: $events")

    for (i in 10 downTo 1) {
        if (i > 0) {
            println("${String.format("%1\$2s", i)}...")
            Thread.sleep(1000L)
        }
    }

    var (_, getResponse, result) = "http://localhost:8080/events/${events.first()}".httpGet().responseString()

    println("GET status: ${getResponse.httpStatusCode}")
    println("GET body: ${result.get()}")
}