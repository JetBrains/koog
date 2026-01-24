package ai.koog.ktor.utils

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import io.ktor.util.logging.KtorSimpleLogger

private val logger = KtorSimpleLogger("ai.koog.ktor.utils.LLMModelParser")

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

    return when (providerName) {
        "openai" -> openAI(parts, identifier)

        "anthropic" -> anthropic(parts, identifier)

        "google" -> google(parts, identifier)

        "mistral" -> mistral(parts, identifier)

        "openrouter" -> openrouter(parts, identifier)

        "deepseek" -> deepSeek(parts, identifier)

        "ollama" -> ollama(parts, identifier)

        else -> {
            logger.debug("Unsupported LLM provider: $providerName")
            null
        }
    }
}

private fun ollama(parts: List<String>, identifier: String): LLModel? {
    if (parts.size < 2) {
        logger.debug(
            "Ollama model identifier must be in format 'ollama.maker.model' or 'ollama.model', got: $identifier"
        )
        return null
    }

    // Special handling for Ollama identifiers to preserve dots in model names
    val ollamaPrefix = "ollama."

    // Check if it's in the format "ollama.maker.model"
    return if (parts.size >= 3) {
        val maker = parts[1].lowercase()

        // Get the model name by removing "ollama.maker." from the identifier
        val makerPrefix = "$ollamaPrefix$maker."
        val modelName = identifier.substring(makerPrefix.length).lowercase()

        when (maker) {
            "groq" -> OLLAMA_GROQ_MODELS_MAP[modelName]
            "meta" -> OLLAMA_META_MODELS_MAP[modelName]
            "alibaba" -> OLLAMA_ALIBABA_MODELS_MAP[modelName]
            else -> null
        }
    } else {
        // Format is "ollama.model"
        val modelName = identifier.substring(ollamaPrefix.length).lowercase()

        OLLAMA_GROQ_MODELS_MAP[modelName]
            ?: OLLAMA_META_MODELS_MAP[modelName]
            ?: OLLAMA_ALIBABA_MODELS_MAP[modelName]
    }
}

private fun openrouter(parts: List<String>, identifier: String): LLModel? {
    if (parts.size < 2) {
        logger.debug("OpenRouter model identifier must be in format 'openrouter.model', got: $identifier")
        return null
    }

    val modelName = parts[1].lowercase()

    // Map for OpenRouter models by name
    val openRouterModels = OPENROUTER_MODELS_MAP

    val normalizedModelName = modelName.replace("-", "").replace("_", "").lowercase()
    val model = openRouterModels[normalizedModelName]
    if (model == null) {
        logger.info("Model '$modelName' not found in OpenRouterModels")
        return null
    }

    return model
}

private fun deepSeek(parts: List<String>, identifier: String): LLModel? {
    if (parts.size < 2) {
        logger.debug("DeepSeek model identifier must be in format 'deepseek.model', got: $identifier")
        return null
    }

    val modelName = parts[1].lowercase()

    // Map for DeepSeek models by name
    val deepSeekModels = DEEPSEEK_MODELS_MAP

    val normalizedModelName = modelName.lowercase()
    val model = deepSeekModels[normalizedModelName]
    if (model == null) {
        logger.info("Model '$modelName' not found in DeepSeekModels")
        return null
    }

    return model
}

private fun google(parts: List<String>, identifier: String): LLModel? {
    if (parts.size < 2) {
        logger.debug("Google model identifier must be in format 'google.model', got: $identifier")
        return null
    }

    val modelName = parts[1].lowercase()

    val normalizedModelName = modelName.replace("-", "_").replace(".", "_").lowercase()
    val model = GOOGLE_MODELS_MAP[normalizedModelName]
    if (model == null) {
        logger.debug("Model '$modelName' not found in GoogleModels")
        return null
    }

    return model
}

private fun mistral(parts: List<String>, identifier: String): LLModel? {
    if (parts.size < 3) {
        logger.debug("Mistral AI model identifier must be in format 'mistral.category.model', got: $identifier")
        return null
    }

    val category = parts[1].lowercase()
    val modelName = parts[2].lowercase()

    val categoryMap = MISTRAL_MODELS_MAP[category]
    if (categoryMap == null) {
        logger.debug("Unknown Mistral AI category: $category")
        return null
    }

    val model = categoryMap[modelName]
    if (model == null) {
        logger.debug("Model '$modelName' not found in Mistral AI category '$category'")
        return null
    }

    return model
}

private fun anthropic(parts: List<String>, identifier: String): LLModel? {
    if (parts.size < 2) {
        logger.debug("Anthropic model identifier must be in format 'anthropic.model', got: $identifier")
        return null
    }

    val modelName = parts[1].lowercase()

    val normalizedModelName = modelName.replace("-", "_").lowercase()
    val model = ANTHROPIC_MODELS_MAP[normalizedModelName]
    if (model == null) {
        logger.debug("Model '$modelName' not found in AnthropicModels")
        return null
    }

    return model
}

private fun openAI(parts: List<String>, identifier: String): LLModel? {
    if (parts.size < 3) {
        logger.debug("OpenAI model identifier must be in format 'openai.category.model', got: $identifier")
        return null
    }

    val category = parts[1].lowercase()
    val modelName = parts[2].lowercase()

    val categoryMap = OPENAI_MODELS_MAP[category]
    if (categoryMap == null) {
        logger.debug("Unknown OpenAI category: $category")
        return null
    }

    val model = categoryMap[modelName]
    if (model == null) {
        logger.debug("Model '$modelName' not found in OpenAI category '$category'")
        return null
    }

    return model
}

private val OPENAI_MODELS_MAP = mapOf(
    "chat" to mapOf(
        "gpt4o" to OpenAIModels.Chat.GPT4o,
        "gpt4_1" to OpenAIModels.Chat.GPT4_1,
        "gpt5" to OpenAIModels.Chat.GPT5,
        "gpt5mini" to OpenAIModels.Chat.GPT5Mini,
        "gpt5nano" to OpenAIModels.Chat.GPT5Nano,
        "o4mini" to OpenAIModels.Chat.O4Mini,
        "o3mini" to OpenAIModels.Chat.O3Mini,
        "o3" to OpenAIModels.Chat.O3,
        "o1" to OpenAIModels.Chat.O1,
        "gpt5codex" to OpenAIModels.Chat.GPT5Codex,
        "gpt5_1" to OpenAIModels.Chat.GPT5_1,
        "gpt5pro" to OpenAIModels.Chat.GPT5Pro,
        "gpt5_1codex" to OpenAIModels.Chat.GPT5_1Codex,
        "gpt5_2" to OpenAIModels.Chat.GPT5_2,
        "gpt5_2pro" to OpenAIModels.Chat.GPT5_2Pro,
        "gpt4_1nano" to OpenAIModels.Chat.GPT4_1Nano,
        "gpt4_1mini" to OpenAIModels.Chat.GPT4_1Mini,
        "gpt4omini" to OpenAIModels.Chat.GPT4oMini,
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
        "omni" to OpenAIModels.Moderation.Omni
    )
)

private val ANTHROPIC_MODELS_MAP = mapOf(
    "opus_3" to AnthropicModels.Opus_3,
    "opus_4" to AnthropicModels.Opus_4,
    "opus_4_1" to AnthropicModels.Opus_4_1,
    "opus_4_5" to AnthropicModels.Opus_4_5,
    "haiku_3" to AnthropicModels.Haiku_3,
    "haiku_3_5" to AnthropicModels.Haiku_3_5,
    "haiku_4_5" to AnthropicModels.Haiku_4_5,
    "sonnet_3_5" to AnthropicModels.Sonnet_3_5,
    "sonnet_3_7" to AnthropicModels.Sonnet_3_7,
    "sonnet_4" to AnthropicModels.Sonnet_4,
    "sonnet_4_5" to AnthropicModels.Sonnet_4_5,
)

private val GOOGLE_MODELS_MAP = mapOf(
    "gemini2_0flash" to GoogleModels.Gemini2_0Flash,
    "gemini2_0flash001" to GoogleModels.Gemini2_0Flash001,
    "gemini2_0flashlite" to GoogleModels.Gemini2_0FlashLite,
    "gemini2_0flashlite001" to GoogleModels.Gemini2_0FlashLite001,
    "gemini2_5pro" to GoogleModels.Gemini2_5Pro,
    "gemini2_5flash" to GoogleModels.Gemini2_5Flash,
    "gemini2_5flashlite" to GoogleModels.Gemini2_5FlashLite,
    "gemini3propreview" to GoogleModels.Gemini3_Pro_Preview,
    "gemini_embedding001" to GoogleModels.Embeddings.GeminiEmbedding001,
)

private val MISTRAL_MODELS_MAP = mapOf(
    "chat" to mapOf(
        "mistral_medium_3_1" to MistralAIModels.Chat.MistralMedium31,
        "mistral_large_2_1" to MistralAIModels.Chat.MistralLarge21,
        "mistral_small_2" to MistralAIModels.Chat.MistralSmall2,
        "magistral_medium_1_2" to MistralAIModels.Chat.MagistralMedium12,
        "codestral" to MistralAIModels.Chat.Codestral,
        "devstral_medium" to MistralAIModels.Chat.DevstralMedium,
    ),
    "embeddings" to mapOf(
        "mistral_embed" to MistralAIModels.Embeddings.MistralEmbed,
        "codestral_embed" to MistralAIModels.Embeddings.CodestralEmbed,
    ),
    "moderation" to mapOf(
        "mistral_moderation" to MistralAIModels.Moderation.MistralModeration
    )
)

private val OPENROUTER_MODELS_MAP = mapOf(
    "claude3haiku" to OpenRouterModels.Claude3Haiku,
    "claude3opus" to OpenRouterModels.Claude3Opus,
    "claude3sonnet" to OpenRouterModels.Claude3Sonnet,
    "claude35sonnet" to OpenRouterModels.Claude3_5Sonnet,
    "claude4sonnet" to OpenRouterModels.Claude4Sonnet,
    "claude41opus" to OpenRouterModels.Claude4_1Opus,
    "gpt35turbo" to OpenRouterModels.GPT35Turbo,
    "gpt4" to OpenRouterModels.GPT4,
    "gpt4turbo" to OpenRouterModels.GPT4Turbo,
    "gpt4o" to OpenRouterModels.GPT4o,
    "gptoss120b" to OpenRouterModels.GPT_OSS_120b,
    "gpt5" to OpenRouterModels.GPT5,
    "gpt5mini" to OpenRouterModels.GPT5Mini,
    "gpt5nano" to OpenRouterModels.GPT5Nano,
    "gpt52" to OpenRouterModels.GPT5_2,
    "gpt52pro" to OpenRouterModels.GPT5_2Pro,
)

private val DEEPSEEK_MODELS_MAP = mapOf(
    "deepseek-chat" to DeepSeekModels.DeepSeekChat,
    "deepseek-reasoner" to DeepSeekModels.DeepSeekReasoner,
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
