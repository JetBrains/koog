package ai.koog.agents.features.debugger

import io.github.oshai.kotlinlogging.KotlinLogging

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object EnvironmentVariablesReader {

    private val logger = KotlinLogging.logger { }

    internal actual fun getEnvironmentVariable(name: String): String? {
        val envVariable = System.getenv(name)
        logger.debug { "Getting environment variable '$name' value: '$envVariable'" }
        return envVariable
    }
}
