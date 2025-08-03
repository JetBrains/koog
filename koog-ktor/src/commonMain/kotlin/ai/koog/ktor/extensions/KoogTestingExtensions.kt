package ai.koog.ktor.extensions

import ai.koog.ktor.Koog
import ai.koog.ktor.KoogAgentsConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install

/**
 * Convenience extension functions for installing Koog with testing and example configurations.
 * These extensions make it easy to set up Koog for different use cases without boilerplate.
 */

/**
 * Installs Koog with mock mode enabled for testing.
 * 
 * This is perfect for unit tests, integration tests, and CI/CD pipelines where you need
 * predictable responses without real LLM calls.
 * 
 * @param configure Additional configuration for the Koog setup
 * @return Configured Koog instance with test-friendly responses
 */
public fun Application.installKoogForTesting(
    configure: KoogAgentsConfig.() -> Unit = {}
): Koog = install(Koog) {
    mockMode()
    configure()
}

/**
 * Installs Koog with mock mode enabled for examples.
 * 
 * This is perfect for examples, demos, and documentation where you want realistic-looking
 * responses without requiring API keys.
 * 
 * @param configure Additional configuration for the Koog setup
 * @return Configured Koog instance with example-friendly responses
 */
public fun Application.installKoogForExamples(
    configure: KoogAgentsConfig.() -> Unit = {}
): Koog = install(Koog) {
    mockMode()
    configure()
}

/**
 * Installs Koog with automatic mode detection.
 * 
 * This will automatically choose the appropriate mode based on the environment:
 * - Test mode for test environments (ktor.test=true)
 * - Example mode for example environments (koog.example.mode=true)
 * - Mock mode if no LLM configuration is found
 * - Real LLM mode if proper configuration is available
 * 
 * @param configure Additional configuration for the Koog setup
 * @return Configured Koog instance with automatically detected mode
 */
public fun Application.installKoogWithAutoDetection(
    configure: KoogAgentsConfig.() -> Unit = {}
): Koog = install(Koog) {
    // The auto-detection is handled by the main plugin installation
    configure()
}

/**
 * Installs Koog with custom mock responses.
 * 
 * This allows you to define specific responses for your application's testing needs.
 * 
 * @param responses Map of patterns to responses for mock behavior
 * @param configure Additional configuration for the Koog setup
 * @return Configured Koog instance with custom mock responses
 */
public fun Application.installKoogWithCustomMocks(
    responses: Map<String, String>,
    configure: KoogAgentsConfig.() -> Unit = {}
): Koog = install(Koog) {
    mockMode {
        responses.forEach { (pattern, response) ->
            addResponse(pattern, response)
        }
    }
    configure()
}

/**
 * Installs Koog in development mode.
 * 
 * This provides a good balance for development work - uses mock responses by default
 * but can be easily switched to real LLMs by providing configuration.
 * 
 * @param configure Additional configuration for the Koog setup
 * @return Configured Koog instance optimized for development
 */
public fun Application.installKoogForDevelopment(
    configure: KoogAgentsConfig.() -> Unit = {}
): Koog = install(Koog) {
    // Use mock mode by default, but allow override through configuration
    if (!autoDetectTestMode()) {
        mockMode()
    }
    configure()
}