package org.abhijitsarkar.camel.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

/**
 * @author Abhijit Sarkar
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Envelope(val events: List<Event>) {
    // http://itsronald.com/blog/2016/06/kotlin-get-property-vs-method/
    val _id: String get() = UUID.randomUUID().toString()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(val id: String, val timestamp: String, val action: String, val target: Target, val actor: Actor)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Target(val repository: String, val tag: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Actor(val name: String)