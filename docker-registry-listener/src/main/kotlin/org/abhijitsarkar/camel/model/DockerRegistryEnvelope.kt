package org.abhijitsarkar.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * @author Abhijit Sarkar
 */
data class Envelope(val events: List<Event>)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(val id: String, val timestamp: String, val action: String, val target: Target, val actor: Actor)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Target(val repository: String, val tag: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Actor(val name: String)