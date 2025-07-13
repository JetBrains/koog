package ai.koog.ktor.utils.config

import ai.koog.ktor.KoogAgentsConfig
import ai.koog.prompt.llm.LLMProvider
import io.ktor.server.config.ApplicationConfig

/**
 * Loads and configures the environment-specific settings for Koog agents based on the provided
 * application configuration. This includes setup for OpenAI, Anthropic, Google, OpenRouter,
 * Ollama, as well as default and fallback LLM (Large Language Model) configurations.
 *
 * @param envConfig The application configuration that contains environment-specific properties
 *                  for configuring Koog agents and associated integrations.
 * @return A populated instance of [KoogAgentsConfig] with the environment-specific settings applied.
 */
internal fun loadEnvironmentConfig(envConfig: ApplicationConfig): KoogAgentsConfig {
    val koogConfig = KoogAgentsConfig()

    // OpenAI configuration
    envConfig.propertyOrNull("koog.openai.apikey")?.getString()?.let { apiKey ->
        if (apiKey.isNotEmpty()) {
            koogConfig.openAI(apiKey) {
                envConfig.propertyOrNull("koog.openai.baseUrl")?.getString()?.let { baseUrl = it }

                // Configure timeouts if present
                if (envConfig.propertyOrNull("koog.openai.timeout") != null) {
                    timeouts {
                        envConfig.propertyOrNull("koog.openai.timeout.requestTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                requestTimeoutMillis = it
                            }
                        envConfig.propertyOrNull("koog.openai.timeout.connectTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                connectTimeoutMillis = it
                            }
                        envConfig.propertyOrNull("koog.openai.timeout.socketTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                socketTimeoutMillis = it
                            }
                    }
                }
            }
        }
    }

    // Anthropic configuration
    envConfig.propertyOrNull("koog.anthropic.apikey")?.getString()?.let { apiKey ->
        if (apiKey.isNotEmpty()) {
            koogConfig.anthropic(apiKey) {
                envConfig.propertyOrNull("koog.anthropic.baseUrl")?.getString()?.let { baseUrl = it }

                // Configure timeouts if present
                if (envConfig.propertyOrNull("koog.anthropic.timeout") != null) {
                    timeouts {
                        envConfig.propertyOrNull("koog.anthropic.timeout.requestTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                requestTimeoutMillis = it
                            }
                        envConfig.propertyOrNull("koog.anthropic.timeout.connectTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                connectTimeoutMillis = it
                            }
                        envConfig.propertyOrNull("koog.anthropic.timeout.socketTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                socketTimeoutMillis = it
                            }
                    }
                }
            }
        }
    }

    // Google configuration
    envConfig.propertyOrNull("koog.google.apikey")?.getString()?.let { apiKey ->
        if (apiKey.isNotEmpty()) {
            koogConfig.google(apiKey) {
                envConfig.propertyOrNull("koog.google.baseUrl")?.getString()?.let { baseUrl = it }

                // Configure timeouts if present
                if (envConfig.propertyOrNull("koog.google.timeout") != null) {
                    timeouts {
                        envConfig.propertyOrNull("koog.google.timeout.requestTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                requestTimeoutMillis = it
                            }
                        envConfig.propertyOrNull("koog.google.timeout.connectTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                connectTimeoutMillis = it
                            }
                        envConfig.propertyOrNull("koog.google.timeout.socketTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                socketTimeoutMillis = it
                            }
                    }
                }
            }
        }
    }

    // OpenRouter configuration
    envConfig.propertyOrNull("koog.openrouter.apikey")?.getString()?.let { apiKey ->
        if (apiKey.isNotEmpty()) {
            koogConfig.openRouter(apiKey) {
                envConfig.propertyOrNull("koog.openrouter.baseUrl")?.getString()?.let { baseUrl = it }

                // Configure timeouts if present
                if (envConfig.propertyOrNull("koog.openrouter.timeout") != null) {
                    timeouts {
                        envConfig.propertyOrNull("koog.openrouter.timeout.requestTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                requestTimeoutMillis = it
                            }
                        envConfig.propertyOrNull("koog.openrouter.timeout.connectTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                connectTimeoutMillis = it
                            }
                        envConfig.propertyOrNull("koog.openrouter.timeout.socketTimeoutMillis")?.getString()
                            ?.toLongOrNull()?.let {
                                socketTimeoutMillis = it
                            }
                    }
                }
            }
        }
    }

    // Ollama configuration
    if (envConfig.propertyOrNull("koog.ollama.enable") != null) {
        koogConfig.ollama {
            envConfig.propertyOrNull("koog.ollama.baseUrl")?.getString()?.let { baseUrl = it }

            // Configure timeouts if present
            if (envConfig.propertyOrNull("koog.ollama.timeout") != null) {
                timeouts {
                    envConfig.propertyOrNull("koog.ollama.timeout.requestTimeoutMillis")?.getString()
                        ?.toLongOrNull()
                        ?.let {
                            requestTimeoutMillis = it
                        }
                    envConfig.propertyOrNull("koog.ollama.timeout.connectTimeoutMillis")?.getString()
                        ?.toLongOrNull()
                        ?.let {
                            connectTimeoutMillis = it
                        }
                    envConfig.propertyOrNull("koog.ollama.timeout.socketTimeoutMillis")?.getString()?.toLongOrNull()
                        ?.let {
                            socketTimeoutMillis = it
                        }
                }
            }
        }
    }

    // Default LLM configuration
    envConfig.propertyOrNull("koog.llm.default")?.getString()?.let { modelIdentifier ->
        try {
            val model = getModelFromIdentifier(modelIdentifier)
            if (model != null) {
                koogConfig.defaultLLM = model
            } else {
                println("Warning: Could not resolve model from identifier '$modelIdentifier'")
            }
        } catch (e: Exception) {
            println("Error resolving default LLM model from identifier '$modelIdentifier': ${e.message}")
        }
    }

    // Fallback LLM configuration
    val fallbackProviderStr = envConfig.propertyOrNull("koog.llm.fallback.provider")?.getString()
    val fallbackModelStr = envConfig.propertyOrNull("koog.llm.fallback.model")?.getString()

    if (fallbackProviderStr != null && fallbackModelStr != null) {
        try {
            val fallbackProvider = when (fallbackProviderStr.lowercase()) {
                "openai" -> LLMProvider.OpenAI
                "anthropic" -> LLMProvider.Anthropic
                "google" -> LLMProvider.Google
                "openrouter" -> LLMProvider.OpenRouter
                "ollama" -> LLMProvider.Ollama
                else -> throw IllegalArgumentException("Unsupported LLM provider: $fallbackProviderStr")
            }

            val fullIdentifier = if (fallbackProviderStr.lowercase() == "openai" && !fallbackModelStr.contains(".")) {
                // For OpenAI, we need to specify a category if not provided
                // Default to "chat" category if not specified
                "$fallbackProviderStr.chat.$fallbackModelStr"
            } else {
                "$fallbackProviderStr.$fallbackModelStr"
            }

            val fallbackModel = getModelFromIdentifier(fullIdentifier)

            if (fallbackModel != null) {
                if (fallbackModel.provider != fallbackProvider) {
                    println("Warning: Model provider (${fallbackModel.provider.id}) does not match specified fallback provider ($fallbackProviderStr)")
                } else {
                    koogConfig.llm {
                        fallback {
                            provider = fallbackProvider
                            model = fallbackModel
                        }
                    }
                }
            } else {
                println("Warning: Could not resolve fallback model from identifier '$fullIdentifier'")
            }
        } catch (e: Exception) {
            println("Error setting up fallback LLM: ${e.message}")
        }
    }

    return koogConfig
}