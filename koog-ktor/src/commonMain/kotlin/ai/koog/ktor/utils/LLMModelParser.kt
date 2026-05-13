package ai.koog.ktor.utils

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.clients.siliconflow.SiliconFlowModels
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
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

        "siliconflow" -> siliconFlow(parts, identifier)

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

private fun siliconFlow(parts: List<String>, identifier: String): LLModel? {
    if (parts.size < 2) {
        logger.debug("SiliconFlow model identifier must be in format 'siliconflow.model', got: $identifier")
        return null
    }

    val modelName = parts[1].lowercase()

    val normalizedModelName = modelName.replace("-", "_").lowercase()
    val model = SILICON_FLOW_MODELS_MAP[normalizedModelName]
    if (model == null) {
        logger.debug("Model '$modelName' not found in SiliconFlowModels")
        return null
    }

    return model
}

private val SILICON_FLOW_MODELS_MAP = mapOf(
    "pangupromoe" to SiliconFlowModels.PanguProMoE,
    "bgererankerv2m3" to SiliconFlowModels.BgeRerankerV2M3,
    "ernie_4_5_300b_a47b" to SiliconFlowModels.ERNIE_4_5_300B_A47B,
    "seedoss_36b_instruct" to SiliconFlowModels.SeedOSS_36B_Instruct,
    "deepseekocr" to SiliconFlowModels.DeepSeekOCR,
    "deepseekr1" to SiliconFlowModels.DeepSeekR1,
    "deepseekr1_0528_qwen3_8b" to SiliconFlowModels.DeepSeekR1_0528_Qwen3_8B,
    "deepseekr1_distill_qwen_14b" to SiliconFlowModels.DeepSeekR1_Distill_Qwen_14B,
    "deepseekr1_distill_qwen32b" to SiliconFlowModels.DeepSeekR1_Distill_Qwen32B,
    "deepseekr1_distill_qwen_7b" to SiliconFlowModels.DeepSeekR1_Distill_Qwen_7B,
    "deepseekv2_5" to SiliconFlowModels.DeepSeekV2_5,
    "deepseekv3" to SiliconFlowModels.DeepSeekV3,
    "deepseekv3_1_terminus" to SiliconFlowModels.DeepSeekV3_1_Terminus,
    "deepseekv3_2" to SiliconFlowModels.DeepSeekV3_2,
    "moss_ttsd_v0_5" to SiliconFlowModels.MOSS_TTSD_V0_5,
    "cosyvoice2_0_5b" to SiliconFlowModels.CosyVoice2_0_5B,
    "sensevoicesmall" to SiliconFlowModels.SenseVoiceSmall,
    "lingflash_2_0" to SiliconFlowModels.LingFlash_2_0,
    "lingmini_2_0" to SiliconFlowModels.LingMini_2_0,
    "ringflash_2_0" to SiliconFlowModels.RingFlash_2_0,
    "indextts_2" to SiliconFlowModels.IndexTTS_2,
    "internlm2_5_7b_chat" to SiliconFlowModels.Internlm2_5_7b_Chat,
    "kolors" to SiliconFlowModels.Kolors,
    "kat_dev" to SiliconFlowModels.KAT_Dev,
    "kimi_k2_instruct_0905" to SiliconFlowModels.Kimi_K2_Instruct_0905,
    "kimi_k2_thinking" to SiliconFlowModels.Kimi_K2_Thinking,
    "bcererankerbasev1" to SiliconFlowModels.BceRerankerBaseV1,
    "paddleocr_vl" to SiliconFlowModels.PaddleOCR_VL,
    "paddleocr_vl_1_5" to SiliconFlowModels.PaddleOCR_VL_1_5,
    "probgereranker_v2_m3" to SiliconFlowModels.ProBgeReranker_V2_M3,
    "prodeepseekr1" to SiliconFlowModels.ProDeepSeekR1,
    "prodeepseekv3" to SiliconFlowModels.ProDeepSeekV3,
    "prodeepseekv3_1_terminus" to SiliconFlowModels.ProDeepSeekV3_1_Terminus,
    "prodeepseekv3_2" to SiliconFlowModels.ProDeepSeekV3_2,
    "prominimax_m2_5" to SiliconFlowModels.ProMiniMax_M2_5,
    "prokimi_k2_instruct_0905" to SiliconFlowModels.ProKimi_K2_Instruct_0905,
    "prokimi_k2_thinking" to SiliconFlowModels.ProKimi_K2_Thinking,
    "prokimi_k2_5" to SiliconFlowModels.ProKimi_K2_5,
    "proqwen2_5_7b_instruct" to SiliconFlowModels.ProQwen2_5_7B_Instruct,
    "proglm4_7" to SiliconFlowModels.ProGLM4_7,
    "proglm5" to SiliconFlowModels.ProGLM5,
    "proglm5_1" to SiliconFlowModels.ProGLM5_1,
    "qwenimage" to SiliconFlowModels.QwenImage,
    "qwenimageedit" to SiliconFlowModels.QwenImageEdit,
    "qwenimageedit_2509" to SiliconFlowModels.QwenImageEdit_2509,
    "qwen2_vl_72b_instruct" to SiliconFlowModels.Qwen2_VL_72B_Instruct,
    "qwen2_5_14b_instruct" to SiliconFlowModels.Qwen2_5_14B_Instruct,
    "qwen2_5_32b_instruct" to SiliconFlowModels.Qwen2_5_32B_Instruct,
    "qwen2_5_72b_instruct" to SiliconFlowModels.Qwen2_5_72B_Instruct,
    "qwen2_5_72b_instruct_128k" to SiliconFlowModels.Qwen2_5_72B_Instruct_128K,
    "qwen2_5_7b_instruct" to SiliconFlowModels.Qwen2_5_7B_Instruct,
    "qwen2_5_coder_32b_instruct" to SiliconFlowModels.Qwen2_5_Coder_32B_Instruct,
    "qwen2_5_vl_32b_instruct" to SiliconFlowModels.Qwen2_5_VL_32B_Instruct,
    "qwen2_5_vl_72b_instruct" to SiliconFlowModels.Qwen2_5_VL_72B_Instruct,
    "qwen3_14b" to SiliconFlowModels.Qwen3_14B,
    "qwen3_235b_a22b_instruct_2507" to SiliconFlowModels.Qwen3_235B_A22B_Instruct_2507,
    "qwen3_235b_a22b_thinking_2507" to SiliconFlowModels.Qwen3_235B_A22B_Thinking_2507,
    "qwen3_30b_a3b_instruct_2507" to SiliconFlowModels.Qwen3_30B_A3B_Instruct_2507,
    "qwen3_30b_a3b_thinking_2507" to SiliconFlowModels.Qwen3_30B_A3B_Thinking_2507,
    "qwen3_32b" to SiliconFlowModels.Qwen3_32B,
    "qwen3_8b" to SiliconFlowModels.Qwen3_8B,
    "qwen3_coder_30b_a3b_instruct" to SiliconFlowModels.Qwen3_Coder_30B_A3B_Instruct,
    "qwen3_coder_480b_a35b_instruct" to SiliconFlowModels.Qwen3_Coder_480B_A35B_Instruct,
    "qwen3_omni_30b_a3b_captioner" to SiliconFlowModels.Qwen3_Omni_30B_A3B_Captioner,
    "qwen3_omni_30b_a3b_instruct" to SiliconFlowModels.Qwen3_Omni_30B_A3B_Instruct,
    "qwen3_omni_30b_a3b_thinking" to SiliconFlowModels.Qwen3_Omni_30B_A3B_Thinking,
    "qwen3_reranker_0_6b" to SiliconFlowModels.Qwen3_Reranker_0_6B,
    "qwen3_reranker_4b" to SiliconFlowModels.Qwen3_Reranker_4B,
    "qwen3_reranker_8b" to SiliconFlowModels.Qwen3_Reranker_8B,
    "qwen3_vl_235b_a22b_instruct" to SiliconFlowModels.Qwen3_VL_235B_A22B_Instruct,
    "qwen3_vl_235b_a22b_thinking" to SiliconFlowModels.Qwen3_VL_235B_A22B_Thinking,
    "qwen3_vl_30b_a3b_instruct" to SiliconFlowModels.Qwen3_VL_30B_A3B_Instruct,
    "qwen3_vl_30b_a3b_thinking" to SiliconFlowModels.Qwen3_VL_30B_A3B_Thinking,
    "qwen3_vl_32b_instruct" to SiliconFlowModels.Qwen3_VL_32B_Instruct,
    "qwen3_vl_32b_thinking" to SiliconFlowModels.Qwen3_VL_32B_Thinking,
    "qwen3_vl_8b_instruct" to SiliconFlowModels.Qwen3_VL_8B_Instruct,
    "qwen3_vl_8b_thinking" to SiliconFlowModels.Qwen3_VL_8B_Thinking,
    "qwen3_5_122b_a10b" to SiliconFlowModels.Qwen3_5_122B_A10B,
    "qwen3_5_27b" to SiliconFlowModels.Qwen3_5_27B,
    "qwen3_5_35b_a3b" to SiliconFlowModels.Qwen3_5_35B_A3B,
    "qwen3_5_397b_a17b" to SiliconFlowModels.Qwen3_5_397B_A17B,
    "qwen3_5_4b" to SiliconFlowModels.Qwen3_5_4B,
    "qwen3_5_9b" to SiliconFlowModels.Qwen3_5_9B,
    "qwq_32b" to SiliconFlowModels.QwQ_32B,
    "step3_5_flash" to SiliconFlowModels.Step3_5_Flash,
    "telespeechasr" to SiliconFlowModels.TeleSpeechASR,
    "hunyuan_a13b_instruct" to SiliconFlowModels.Hunyuan_A13B_Instruct,
    "hunyuan_mt_7b" to SiliconFlowModels.Hunyuan_MT_7B,
    "glm4_32b_0414" to SiliconFlowModels.GLM4_32B_0414,
    "glm4_9b_0414" to SiliconFlowModels.GLM4_9B_0414,
    "glm4_1v_9b_thinking" to SiliconFlowModels.GLM4_1V_9B_Thinking,
    "glm_z1_32b_0414" to SiliconFlowModels.GLM_Z1_32B_0414,
    "glm_z1_9b_0414" to SiliconFlowModels.GLM_Z1_9B_0414,
    "wan2_2_i2v_a14b" to SiliconFlowModels.Wan2_2_I2V_A14B,
    "wan2_2_t2v_a14b" to SiliconFlowModels.Wan2_2_T2V_A14B,
    "glm4_5_air" to SiliconFlowModels.GLM4_5_Air,
    "glm4_5v" to SiliconFlowModels.GLM4_5V,
    "glm4_6" to SiliconFlowModels.GLM4_6,
    "glm4_6v" to SiliconFlowModels.GLM4_6V,
    "bgelarge_en_v1_5" to SiliconFlowModels.Embeddings.BgeLarge_En_V1_5,
    "bgelarge_zh_v1_5" to SiliconFlowModels.Embeddings.BgeLarge_Zh_V1_5,
    "bgem3" to SiliconFlowModels.Embeddings.BgeM3,
    "bceembedding_base_v1" to SiliconFlowModels.Embeddings.BceEmbedding_Base_V1,
    "probgem3" to SiliconFlowModels.Embeddings.ProBgeM3,
    "qwen3_embedding_0_6b" to SiliconFlowModels.Embeddings.Qwen3_Embedding_0_6B,
    "qwen3_embedding_4b" to SiliconFlowModels.Embeddings.Qwen3_Embedding_4B,
    "qwen3_embedding_8b" to SiliconFlowModels.Embeddings.Qwen3_Embedding_8B,
)

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
        "gpt5_1codexmax" to OpenAIModels.Chat.GPT5_1CodexMax,
        "gpt5_2" to OpenAIModels.Chat.GPT5_2,
        "gpt5_2pro" to OpenAIModels.Chat.GPT5_2Pro,
        "gpt5_2codex" to OpenAIModels.Chat.GPT5_2Codex,
        "gpt5_3codex" to OpenAIModels.Chat.GPT5_3Codex,
        "gpt5_4nano" to OpenAIModels.Chat.GPT5_4Nano,
        "gpt5_4mini" to OpenAIModels.Chat.GPT5_4Mini,
        "gpt5_4" to OpenAIModels.Chat.GPT5_4,
        "gpt5_4pro" to OpenAIModels.Chat.GPT5_4Pro,
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
    "opus_4" to AnthropicModels.Opus_4,
    "opus_4_1" to AnthropicModels.Opus_4_1,
    "opus_4_5" to AnthropicModels.Opus_4_5,
    "opus_4_6" to AnthropicModels.Opus_4_6,
    "opus_4_7" to AnthropicModels.Opus_4_7,
    "haiku_3" to AnthropicModels.Haiku_3,
    "haiku_4_5" to AnthropicModels.Haiku_4_5,
    "sonnet_4" to AnthropicModels.Sonnet_4,
    "sonnet_4_5" to AnthropicModels.Sonnet_4_5,
    "sonnet_4_6" to AnthropicModels.Sonnet_4_6,
)

private val GOOGLE_MODELS_MAP = mapOf(
    "gemini2_0flash" to GoogleModels.Gemini2_0Flash,
    "gemini2_0flash001" to GoogleModels.Gemini2_0Flash001,
    "gemini2_0flashlite" to GoogleModels.Gemini2_0FlashLite,
    "gemini2_0flashlite001" to GoogleModels.Gemini2_0FlashLite001,
    "gemini2_5flash" to GoogleModels.Gemini2_5Flash,
    "gemini2_5flashlite" to GoogleModels.Gemini2_5FlashLite,
    "gemini2_5pro" to GoogleModels.Gemini2_5Pro,
    "gemini3flashpreview" to GoogleModels.Gemini3_Flash_Preview,
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
    "deepseek-v4-flash" to DeepSeekModels.DeepSeekV4Flash,
    "deepseek-v4-pro" to DeepSeekModels.DeepSeekV4Pro,
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
