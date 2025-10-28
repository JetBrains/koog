package ai.koog.agents.features.debugger

import io.github.oshai.kotlinlogging.KotlinLogging

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect object SystemVariablesReader {

    /**
     * Retrieves the value of a specified environment variable.
     *
     * @param name The name of the environment variable to retrieve.
     * @return The value of the specified environment variable as a String, or null if the variable is not set.
     */
    internal fun getEnvironmentVariable(name: String): String?

    /**
     * Retrieves the value of a specified virtual machine (VM) option.
     *
     * @param name The name of the VM option to retrieve.
     * @return The value of the specified VM option as a String, or null if the option is not set.
     */
    internal fun getVMOption(name: String): String?
}

private val logger = KotlinLogging.logger { }

/**
 * Reads the value of a specified environment variable.
 *
 * @param name The name of the environment variable to retrieve.
 * @return The value of the specified environment variable as a String, or null if the variable is not set or cannot be read.
 */
public fun readEnvironmentVariable(name: String): String? {
    logger.trace { "Start reading env variable: $name" }

    val value = try {
        SystemVariablesReader.getEnvironmentVariable(name)
    } catch (e: NotImplementedError) {
        logger.debug { "Unable to read env variable for the current platform: ${e.message}" }
        null
    }

    logger.debug { "Read env variable: $name=$value" }
    return value
}

/**
 * Reads the value of a specified virtual machine (VM) option.
 *
 * @param name The name of the VM option to retrieve.
 * @return The value of the specified VM option as a String, or null if the option is not set or cannot be read.
 */
public fun readVMOption(name: String): String? {
    logger.trace { "Start reading VM option: $name" }

    val value = try {
        SystemVariablesReader.getVMOption(name)
    } catch (e: NotImplementedError) {
        logger.debug { "Unable to read VM option for the current platform: ${e.message}" }
        null
    }

    logger.debug { "Read VM option: $name=$value" }
    return value
}
