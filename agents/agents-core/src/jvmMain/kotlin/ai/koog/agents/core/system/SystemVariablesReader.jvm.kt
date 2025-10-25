package ai.koog.agents.core.system

import io.github.oshai.kotlinlogging.KotlinLogging

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
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
