package org.abhijitsarkar.camel.util

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNot
import io.kotlintest.specs.ShouldSpec
import org.abhijitsarkar.camel.Application
import org.abhijitsarkar.camel.model.Actor
import org.abhijitsarkar.camel.model.Event
import org.abhijitsarkar.camel.model.Target
import java.io.File

/**
 * @author Abhijit Sarkar
 */
class FileUtilSpec : ShouldSpec() {
    init {
        should("update mysql") {
            val file = File(javaClass.getResource("/${Application.DOCKER_IMAGES_FILENAME}").toURI())

            val events = listOf(Event(
                    "1",
                    "whatever",
                    "push",
                    Target("library/mysql", "2.0.0"),
                    Actor("whoever")
            ))

            val (keysUpdated, dockerImages) = file.updateIfNecessary(events)

            keysUpdated?.keys shouldNot beEmpty()
            keysUpdated["1"] shouldBe listOf("mysql")

            dockerImages.size shouldBe 2
            dockerImages["couchbase"] shouldBe "library/couchbase:1.0.0"
            dockerImages["mysql"] shouldBe "library/mysql:2.0.0"
        }
    }
}