package ai.koog.ktor.utils

import ai.koog.ktor.KoogAgentsConfig
import ai.koog.prompt.llm.LLMProvider
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.milliseconds

/**
 * Loads and configures the environment-specific settings for Koog agents based on the provided
 * application configuration.
 * This includes setup for OpenAI, Anthropic, Google, MistralAI, OpenRouter, DeepSeek,
 * Ollama, as well as default and fallback LLM (Large Language Model) configurations.
 *
 * @return A populated instance of [KoogAgentsConfig] with the environment-specific settings applied.
 */
internal fun ApplicationEnvironment.loadAgentsConfig(scope: CoroutineScope): KoogAgentsConfig {
    val koogConfig = KoogAgentsConfig(scope)
        .openAI(config)
        .anthropic(config)
        .google(config)
        .mistral(config)
        .openrouter(config)
        .deepSeek(config)

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
            "mistral" -> LLMProvider.MistralAI
            "openrouter" -> LLMProvider.OpenRouter
            "ollama" -> LLMProvider.Ollama
            "deepseek" -> LLMProvider.DeepSeek
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
    if (envConfig.propertyOrNull("koog.ollama") != null) {
        ollama {
            envConfig.propertyOrNull("koog.ollama.baseUrl")?.getString()?.let { baseUrl = it }
            timeouts { configure("koog.ollama.timeout", envConfig) }
        }
    }
}

private fun KoogAgentsConfig.openrouter(envConfig: ApplicationConfig) = apply {
    config(envConfig, "koog.openrouter") { apiKey, baseUrlOrNull ->
        openRouter(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure("koog.openrouter.timeout", envConfig) }
        }
    }
}

private fun KoogAgentsConfig.deepSeek(envConfig: ApplicationConfig) =
    config(envConfig, "koog.deepseek") { apiKey, baseUrlOrNull ->
        deepSeek(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure("koog.deepseek.timeout", envConfig) }
        }
    }

private fun KoogAgentsConfig.google(envConfig: ApplicationConfig) =
    config(envConfig, "koog.google") { apiKey, baseUrlOrNull ->
        google(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure("koog.google.timeout", envConfig) }
        }
    }

private fun KoogAgentsConfig.mistral(envConfig: ApplicationConfig) =
    config(envConfig, "koog.mistral") { apiKey, baseUrlOrNull ->
        mistral(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure("koog.mistral.timeout", envConfig) }
        }
    }

private fun KoogAgentsConfig.openAI(envConfig: ApplicationConfig) =
    config(envConfig, "koog.openai") { apiKey, baseUrlOrNull ->
        openAI(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure("koog.openai.timeout", envConfig) }
        }
    }

private fun KoogAgentsConfig.anthropic(envConfig: ApplicationConfig) =
    config(envConfig, "koog.anthropic") { apiKey, baseUrlOrNull ->
        anthropic(apiKey) {
            baseUrlOrNull?.let { baseUrl = it }
            timeouts { configure("koog.anthropic.timeout", envConfig) }
        }
    }

private inline fun KoogAgentsConfig.config(
    appConfig: ApplicationConfig,
    key: String,
    block: (String, String?) -> Unit
) = apply {
    appConfig.propertyOrNull(key) ?: return@apply
    val config = appConfig.config(key)
    val apiKey = config.propertyOrNull("apikey")?.getString()
    requireNotNull(apiKey) { "Found $key but apiKey was missing." }
    block(apiKey, config.propertyOrNull("baseUrl")?.getString())
}

private fun KoogAgentsConfig.TimeoutConfiguration.configure(key: String, config: ApplicationConfig) {
    if (config.propertyOrNull(key) == null) {
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
}
