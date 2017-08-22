package org.abhijitsarkar.camel.router

/**
 * @author Abhijit Sarkar
 */
interface RouterUtil {
    fun logUri(clazz: Class<Any>) = "log:${clazz.name}?level=DEBUG&showException=true&showHeaders=true" +
            "&showOut=true&showStackTrace=true"

    val numCores: Int get() = Runtime.getRuntime().availableProcessors()
    val sedaOptions: String get() = "waitForTaskToComplete=Never&purgeWhenStopping=true&concurrentConsumers=$numCores"
}