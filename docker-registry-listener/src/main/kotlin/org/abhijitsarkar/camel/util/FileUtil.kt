package org.abhijitsarkar.camel.util

import org.abhijitsarkar.camel.model.Event
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.Objects

/**
 * @author Abhijit Sarkar
 */

data class DockerImage(private val prefix: String, val name: String, val tag: String) {
    override fun toString(): String {
        return "$prefix$name:$tag"
    }
}

private fun String.toDockerImage(): DockerImage? {
    val groups = "^(.+)(?<=/)(.+):(.+)\$".toRegex()
            .matchEntire(this as CharSequence)
            ?.groupValues

    return if (groups != null && groups.size >= 4) {
        DockerImage(groups[1], groups[2], groups[3])
    } else null
}

fun File.updateIfNecessary(events: List<Event>): Pair<Map<String, List<String>>, Map<String, String>> =
        this.bufferedReader().use {
            @Suppress("UNCHECKED_CAST")
            val dockerImages = Yaml().load(it) as MutableMap<String, String>
            val imagesUpdated = mutableMapOf<String, List<String>>()

            dockerImages
                    .mapValues { (_, v) -> v.toDockerImage() }
                    .filter(Objects::nonNull)
                    .map { (k, v) ->
                        events
                                .find {
                                    it.target.repository.endsWith(v!!.name)
                                            && it.target.tag != v.tag
                                }
                                ?.let {
                                    Triple(it.id, k, v!!.copy(tag = it.target.tag))
                                }
                    }
                    .filter(Objects::nonNull)
                    .forEach {
                        dockerImages[it!!.second] = it.third.toString()
                        imagesUpdated.merge(it.first, listOf(it.second), { k1, k2 -> k1 + k2 })
                    }

            // no first-class support for mutable to immutable map
            imagesUpdated.to(dockerImages)
        }