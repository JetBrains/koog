package ai.koog.ktor.extensions

import ai.koog.ktor.Koog
import ai.koog.ktor.KoogAgentsConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install

/**
 * Convenience extension functions for installing Koog with custom mock configurations.
 */

/**
 * Installs Koog with custom mock responses.
 * 
 * This allows you to define specific responses for your application's testing needs.
 * For simple mock mode, use `install(Koog) { mockMode() }` directly.
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