package org.abhijitsarkar.camel.model

/**
 * @author Abhijit Sarkar
 */
data class AuditRecord(
        val eventId: String,
        val repoName: String,
        val branch: String,
        val keyUpdated: List<String>?
)