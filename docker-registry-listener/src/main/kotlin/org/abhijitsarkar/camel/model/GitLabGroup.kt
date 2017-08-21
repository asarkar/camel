package org.abhijitsarkar.camel.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author Abhijit Sarkar
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Group(val name: String, val projects: List<Project>)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Project(val name: String) {
    @JsonProperty("ssh_url_to_repo")
    lateinit var sshUrl: String
}