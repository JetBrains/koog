package ai.koog.ktor.utils

import ai.koog.ktor.KoogAgentsConfig
import ai.koog.prompt.llm.LLMProvider
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.milliseconds

/**
 * Loads and configures the environment-specific settings for Koog agents based on the provided
 * application configuration. This includes setup for OpenAI, Anthropic, Google, OpenRouter,
 * Ollama, as well as default and fallback LLM (Large Language Model) configurations.
 *
 * @param envConfig The application configuration that contains environment-specific properties
 *                  for configuring Koog agents and associated integrations.
 * @return A populated instance of [KoogAgentsConfig] with the environment-specific settings applied.
 */
internal fun ApplicationEnvironment.loadAgentsConfig(scope: CoroutineScope): KoogAgentsConfig {
    val koogConfig = KoogAgentsConfig(scope)
        .openAI(config)
        .anthropic(config)
        .google(config)
        .openrouter(config)

    if (config.propertyOrNull("koog.ollama.enable") != null) {
        koogConfig.ollama(config)
    }

    val fallbackProviderStr = config.propertyOrNull("koog.llm.fallback.provider")?.getString()
    val fallbackModelStr = config.propertyOrNull("koog.llm.fallback.model")?.getString()

    if (fallbackProviderStr != null && fallbackModelStr != null) {
        val fallbackProvider = when (fallbackProviderStr.lowercase()) {
            "openai" -> LLMProvider.OpenAI
            "anthropic" -> LLMProvider.Anthropic
            "google" -> LLMProvider.Google
            "openrouter" -> LLMProvider.OpenRouter
            "ollama" -> LLMProvider.Ollama
            else -> throw IllegalArgumentException("Unsupported LLM provider: $fallbackProviderStr")
        }

        val fullIdentifier =
            if (fallbackProviderStr.lowercase() == "openai" && !fallbackModelStr.contains(".")) {
                // For OpenAI, we need to specify a category if not provided
                // Default to "chat" category if not specified
                "$fallbackProviderStr.chat.$fallbackModelStr"
            } else {
                "$fallbackProviderStr.$fallbackModelStr"
            }

        val fallbackModel = getModelFromIdentifier(fullIdentifier)

        when {
            fallbackModel != null && fallbackModel.provider != fallbackProvider ->
                log.warn(
                    "Model provider (${fallbackModel.provider.id}) does not match specified fallback provider ($fallbackProviderStr)"
                )

            fallbackModel != null -> koogConfig.llm {
                fallback {
                    provider = fallbackProvider
                    model = fallbackModel
                }
            }

            else -> log.warn("Could not resolve fallback model from identifier '$fullIdentifier'")
        }
    }

    return koogConfig
}

private fun KoogAgentsConfig.ollama(envConfig: ApplicationConfig) = apply {
    ollama {
        envConfig.propertyOrNull("koog.ollama.baseUrl")?.getString()?.let { baseUrl = it }
        timeouts { configure(envConfig.config("koog.ollama.timeout")) }
    }
}

private fun KoogAgentsConfig.openrouter(envConfig: ApplicationConfig) =
    config(envConfig.config("koog.openrouter")) { apiKey, baseUrlOrNull ->
        openRouter(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure(envConfig.config("timeout")) }
        }
    }

private fun KoogAgentsConfig.google(envConfig: ApplicationConfig) =
    config(envConfig.config("koog.google")) { apiKey, baseUrlOrNull ->
        google(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure(envConfig.config("timeout")) }
        }
    }

private fun KoogAgentsConfig.openAI(envConfig: ApplicationConfig) =
    config(envConfig.config("koog.openai")) { apiKey, baseUrlOrNull ->
        openAI(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure(envConfig.config("timeout")) }
        }
    }

private fun KoogAgentsConfig.anthropic(envConfig: ApplicationConfig) =
    config(envConfig.config("koog.anthropic")) { apiKey, baseUrlOrNull ->
        anthropic(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure(envConfig.config("timeout")) }
        }
    }

private inline fun KoogAgentsConfig.config(config: ApplicationConfig, block: (String, String?) -> Unit) = apply {
    config.propertyOrNull("apikey")?.getString()?.let { apiKey ->
        block(apiKey, config.propertyOrNull("baseUrl")?.getString())
    }
}

private fun KoogAgentsConfig.TimeoutConfiguration.configure(config: ApplicationConfig) {
    config.propertyOrNull("requestTimeoutMillis")
        ?.getString()
        ?.toLongOrNull()
        ?.let { requestTimeout = it.milliseconds }

    config.propertyOrNull("connectTimeoutMillis")
        ?.getString()
        ?.toLongOrNull()
        ?.let { connectTimeout = it.milliseconds }
    config.propertyOrNull("socketTimeoutMillis")
        ?.getString()
        ?.toLongOrNull()
        ?.let { socketTimeout = it.milliseconds }
}
