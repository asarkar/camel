package org.abhijitsarkar.camel

import com.mongodb.MongoClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile

/**
 * @author Abhijit Sarkar
 */
@SpringBootApplication
class Application {
    companion object {
        const val DOCKER_REGISTRY_EVENTS_HEADER = "DockerRegistryEvents"
        const val DOCKER_IMAGES_FILENAME = "docker-images.yml"
    }

    @Bean
    @Profile("!it")
    fun mongoBean(@Value("\${mongodb.host:localhost}") host: String, @Value("\${mongodb.port:27017}") port: Int) =
            MongoClient(host, port)
}

fun main(args: Array<String>) {
    SpringApplicationBuilder(Application::class.java)
            .web(false)
            .run(*args)
}