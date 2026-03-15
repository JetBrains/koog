package ai.koog.agents.core.system

import io.github.oshai.kotlinlogging.KotlinLogging

internal actual object SystemVariablesReader {

    private val logger = KotlinLogging.logger { }

    internal actual fun getEnvironmentVariable(name: String): String? {
        val value = System.getenv(name)
        logger.debug { "Getting environment variable '$name' value: '$value'" }
        return value
    }

    internal actual fun getVMOption(name: String): String? {
        val value = System.getProperty(name)
        logger.debug { "Getting VM Option '$name' value: '$value'" }
        return value
    }
}
