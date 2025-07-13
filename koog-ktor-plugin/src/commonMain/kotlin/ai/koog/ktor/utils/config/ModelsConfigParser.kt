package ai.koog.ktor.utils.config

import ai.koog.ktor.KoogAgentsConfig
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.toLLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


/**
 * Gets a model from a string identifier in the format "provider.category.model" or "provider.model".
 * For example, "openai.chat.gpt4o" would resolve to OpenAIModels.Chat.GPT4o.
 *
 * @param identifier The string identifier of the model.
 * @return The resolved LLModel or null if the model cannot be resolved.
 */
internal fun getModelFromIdentifier(identifier: String): LLModel? {
    val parts = identifier.split(".")

    if (parts.isEmpty()) {
        return null
    }

    val providerName = parts[0].lowercase()

    when (providerName) {
        "openai" -> {
            // OpenAI has a nested structure with categories
            if (parts.size < 3) {
                println("OpenAI model identifier must be in format 'openai.category.model', got: $identifier")
                return null
            }

            val category = parts[1].lowercase()
            val modelName = parts[2].lowercase()

            // Map for OpenAI models by category and name
            val openaiModels = OPENAI_MODELS_MAP

            val categoryMap = openaiModels[category]
            if (categoryMap == null) {
                println("Unknown OpenAI category: $category")
                return null
            }

            val model = categoryMap[modelName]
            if (model == null) {
                println("Model '$modelName' not found in OpenAI category '$category'")
                return null
            }

            return model
        }

        "anthropic" -> {
            if (parts.size < 2) {
                println("Anthropic model identifier must be in format 'anthropic.model', got: $identifier")
                return null
            }

            val modelName = parts[1].lowercase()

            // Map for Anthropic models by name
            val anthropicModels = ANTHROPIC_MODELS_MAP

            val normalizedModelName = modelName.replace("-", "_").lowercase()
            val model = anthropicModels[normalizedModelName]
            if (model == null) {
                println("Model '$modelName' not found in AnthropicModels")
                return null
            }

            return model
        }

        "google" -> {
            if (parts.size < 2) {
                println("Google model identifier must be in format 'google.model', got: $identifier")
                return null
            }

            val modelName = parts[1].lowercase()

            // Map for Google models by name
            val googleModels = GOOGLE_MODELS_MAP

            val normalizedModelName = modelName.replace("-", "_").replace(".", "_").lowercase()
            val model = googleModels[normalizedModelName]
            if (model == null) {
                println("Model '$modelName' not found in GoogleModels")
                return null
            }

            return model
        }

        "openrouter" -> {
            if (parts.size < 2) {
                println("OpenRouter model identifier must be in format 'openrouter.model', got: $identifier")
                return null
            }

            val modelName = parts[1].lowercase()

            // Map for OpenRouter models by name
            val openRouterModels = OPENROUTER_MODELS_MAP

            val normalizedModelName = modelName.replace("-", "").replace("_", "").lowercase()
            val model = openRouterModels[normalizedModelName]
            if (model == null) {
                println("Model '$modelName' not found in OpenRouterModels")
                return null
            }

            return model
        }

        "ollama" -> {
            if (parts.size < 2) {
                println("Ollama model identifier must be in format 'ollama.maker.model' or 'ollama.model', got: $identifier")
                return null
            }

            // Special handling for Ollama identifiers to preserve dots in model names
            val ollamaPrefix = "ollama."

            // Check if it's in the format "ollama.maker.model"
            if (parts.size >= 3) {
                val maker = parts[1].lowercase()

                // Get the model name by removing "ollama.maker." from the identifier
                val makerPrefix = ollamaPrefix + maker + "."
                val modelName = identifier.substring(makerPrefix.length).lowercase()

                when (maker) {
                    "groq" -> {
                        val groqModels = OLLAMA_GROQ_MODELS_MAP
                        return groqModels[modelName]
                    }

                    "meta" -> {
                        val metaModels = OLLAMA_META_MODELS_MAP
                        return metaModels[modelName]
                    }

                    "alibaba" -> {
                        val alibabaModels = OLLAMA_ALIBABA_MODELS_MAP
                        return alibabaModels[modelName]
                    }

                    else -> {
                        println("Unknown Ollama maker: $maker")
                        return null
                    }
                }
            } else {
                // Format is "ollama.model"
                val modelName = identifier.substring(ollamaPrefix.length).lowercase()

                val knownModel = OLLAMA_GROQ_MODELS_MAP[modelName] ?: OLLAMA_META_MODELS_MAP[modelName]
                ?: OLLAMA_ALIBABA_MODELS_MAP[modelName]

                if (knownModel != null) {
                    return knownModel
                }

                println(
                    "Model '$modelName' not found in OllamaModels."
                )
                return null
            }
        }

        else -> {
            println("Unsupported LLM provider: $providerName")
            return null
        }
    }
}

private val OPENAI_MODELS_MAP = mapOf(
    "chat" to mapOf(
        "gpt4o" to OpenAIModels.Chat.GPT4o,
        "gpt4_1" to OpenAIModels.Chat.GPT4_1
    ),
    "reasoning" to mapOf(
        "gpt4omini" to OpenAIModels.Reasoning.GPT4oMini,
        "o3mini" to OpenAIModels.Reasoning.O3Mini,
        "o1mini" to OpenAIModels.Reasoning.O1Mini,
        "o3" to OpenAIModels.Reasoning.O3,
        "o1" to OpenAIModels.Reasoning.O1
    ),
    "costoptimized" to mapOf(
        "o4mini" to OpenAIModels.CostOptimized.O4Mini,
        "gpt4_1nano" to OpenAIModels.CostOptimized.GPT4_1Nano,
        "gpt4_1mini" to OpenAIModels.CostOptimized.GPT4_1Mini,
        "gpt4omini" to OpenAIModels.CostOptimized.GPT4oMini,
        "o1mini" to OpenAIModels.CostOptimized.O1Mini,
        "o3mini" to OpenAIModels.CostOptimized.O3Mini
    ),
    "audio" to mapOf(
        "gpt4ominiaudio" to OpenAIModels.Audio.GPT4oMiniAudio,
        "gpt4oaudio" to OpenAIModels.Audio.GPT4oAudio
    ),
    "embeddings" to mapOf(
        "textembedding3small" to OpenAIModels.Embeddings.TextEmbedding3Small,
        "textembedding3large" to OpenAIModels.Embeddings.TextEmbedding3Large,
        "textembeddingada002" to OpenAIModels.Embeddings.TextEmbeddingAda002
    ),
    "moderation" to mapOf(
        "text" to OpenAIModels.Moderation.Text,
        "omni" to OpenAIModels.Moderation.Omni
    )
)

private val ANTHROPIC_MODELS_MAP = mapOf(
    "opus_3" to AnthropicModels.Opus_3,
    "opus_4" to AnthropicModels.Opus_4,
    "haiku_3" to AnthropicModels.Haiku_3,
    "haiku_3_5" to AnthropicModels.Haiku_3_5,
    "sonnet_3_5" to AnthropicModels.Sonnet_3_5,
    "sonnet_3_7" to AnthropicModels.Sonnet_3_7,
    "sonnet_4" to AnthropicModels.Sonnet_4
)

private val GOOGLE_MODELS_MAP = mapOf(
    "gemini1_5pro" to GoogleModels.Gemini1_5Pro,
    "gemini1_5prolatest" to GoogleModels.Gemini1_5ProLatest,
    "gemini2_5propreview0506" to GoogleModels.Gemini2_5ProPreview0506,
    "gemini2_0flash" to GoogleModels.Gemini2_0Flash,
    "gemini2_0flash001" to GoogleModels.Gemini2_0Flash001,
    "gemini2_0flashlite" to GoogleModels.Gemini2_0FlashLite,
    "gemini2_0flashlite001" to GoogleModels.Gemini2_0FlashLite001,
    "gemini1_5flash" to GoogleModels.Gemini1_5Flash,
    "gemini1_5flashlatest" to GoogleModels.Gemini1_5FlashLatest,
    "gemini1_5flash002" to GoogleModels.Gemini1_5Flash002,
    "gemini1_5flash8b" to GoogleModels.Gemini1_5Flash8B,
    "gemini1_5flash8b001" to GoogleModels.Gemini1_5Flash8B001,
    "gemini1_5flash8blatest" to GoogleModels.Gemini1_5Flash8BLatest,
    "gemini2_5flashpreview0417" to GoogleModels.Gemini2_5FlashPreview0417
)

private val OPENROUTER_MODELS_MAP = mapOf(
    "claude3sonnet" to OpenRouterModels.Claude3Sonnet,
    "claude3haiku" to OpenRouterModels.Claude3Haiku,
    "gpt4" to OpenRouterModels.GPT4,
    "gpt4o" to OpenRouterModels.GPT4o,
    "gpt4turbo" to OpenRouterModels.GPT4Turbo,
    "gpt35turbo" to OpenRouterModels.GPT35Turbo
)

private val OLLAMA_GROQ_MODELS_MAP = mapOf(
    "llama3-grok-tool-use:8b" to OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B,
    "llama3-groq-tool-use:8b" to OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B,
    "llama3-grok-tool-use:70b" to OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_70B,
    "llama3-groq-tool-use:70b" to OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_70B
)

private val OLLAMA_META_MODELS_MAP = mapOf(
    "llama3.2:3b" to OllamaModels.Meta.LLAMA_3_2_3B,
    "llama3.2" to OllamaModels.Meta.LLAMA_3_2,
    "llama4:latest" to OllamaModels.Meta.LLAMA_4,
    "llama-guard3:latest" to OllamaModels.Meta.LLAMA_GUARD_3
)

private val OLLAMA_ALIBABA_MODELS_MAP = mapOf(
    "qwen2.5:0.5b" to OllamaModels.Alibaba.QWEN_2_5_05B,
    "qwen3:0.6b" to OllamaModels.Alibaba.QWEN_3_06B,
    "qwq:32b" to OllamaModels.Alibaba.QWQ_32B,
    "qwq" to OllamaModels.Alibaba.QWQ,
    "qwen2.5-coder:32b" to OllamaModels.Alibaba.QWEN_CODER_2_5_32B
)
