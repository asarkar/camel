package org.abhijitsarkar.camel.util

import org.abhijitsarkar.camel.model.Event
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * @author Abhijit Sarkar
 */
fun File.updateIfNecessary(events: List<Event>): Pair<Map<String, List<String>>, Map<String, String>> =
        this.bufferedReader().use {
            val dockerImages = Yaml().load(it) as MutableMap<String, String>
            var keysUpdated = mutableMapOf<String, List<String>>()

            dockerImages.keys.forEach { k ->
                val v = dockerImages[k]
                val groups = "^(.+)(?<=/)(.+):(.+)\$".toRegex()
                        .matchEntire(v as CharSequence)
                        ?.groupValues

                if (groups != null && groups.size >= 4) {
                    val repository = groups[2]
                    val tag = groups[3]

                    val event = events
                            .find { e -> e.target.repository.endsWith(repository) && e.target.tag != tag }

                    if (event != null) {
                        dockerImages[k] = "${groups[1]}${groups[2]}:${event.target.tag}"
                        keysUpdated.merge(event.id, listOf(k), { k1, k2 -> k1 + k2 })
                    }
                }
            }

            // no first-class support for mutable to immutable map
            keysUpdated.to(dockerImages)
        }