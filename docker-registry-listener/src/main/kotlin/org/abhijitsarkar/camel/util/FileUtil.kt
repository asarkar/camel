package org.abhijitsarkar.camel.util

import org.abhijitsarkar.camel.model.Event
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * @author Abhijit Sarkar
 */
fun File.updateIfNecessary(events: List<Event>): Pair<Map<String, List<String>>, Map<String, String>> =
        this.bufferedReader().use {
            @Suppress("UNCHECKED_CAST")
            val dockerImages = Yaml().load(it) as MutableMap<String, String>
            var imagesUpdated = mutableMapOf<String, List<String>>()

            dockerImages.keys.forEach { image ->
                val v = dockerImages[image]
                val groups = "^(.+)(?<=/)(.+):(.+)\$".toRegex()
                        .matchEntire(v as CharSequence)
                        ?.groupValues

                if (groups != null && groups.size >= 4) {
                    val repository = groups[2]
                    val tag = groups[3]

                    val event = events
                            .find { e -> e.target.repository.endsWith(repository) && e.target.tag != tag }

                    if (event != null) {
                        dockerImages[image] = "${groups[1]}${groups[2]}:${event.target.tag}"
                        imagesUpdated.merge(event.id, listOf(image), { k1, k2 -> k1 + k2 })
                    }
                }
            }

            // no first-class support for mutable to immutable map
            imagesUpdated.to(dockerImages)
        }