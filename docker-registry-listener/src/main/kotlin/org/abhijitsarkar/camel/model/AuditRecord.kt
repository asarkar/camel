package org.abhijitsarkar.camel.model

import java.util.UUID

/**
 * @author Abhijit Sarkar
 */
data class AuditRecord(
        val eventId: String,
        val repoName: String,
        val branch: String,
        val imagesUpdated: List<String>?
) {
    // http://itsronald.com/blog/2016/06/kotlin-get-property-vs-method/
    val _id: String get() = UUID.randomUUID().toString()
}