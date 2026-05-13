package ai.koog.prompt.executor.clients.litert

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/** LLM provider identifier for on-device Android inference via the LiteRT runtime. */
public data object LiteRTLLMProvider : LLMProvider("android-litert", "LiteRT")

/**
 * Catalog of [LLModel] definitions available for on-device Android inference via LiteRT.
 *
 * Implements [LLModelDefinitions] so models registered here are discoverable by the
 * koog executor infrastructure. Custom models can be added at runtime via [addCustomModel].
 */
public object LiteRTLLModels : LLModelDefinitions {
    /**
     * A fine-tuned Gemma variant optimized for function-calling on device.
     *
     * Model file: `mobile_actions_q8_ekv1024.litertlm`
     * Source: https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions/tree/main
     */
    public val FunctionGemma: LLModel = LLModel(
        provider = LiteRTLLMProvider,
        id = "mobile_actions_q8_ekv1024.litertlm",
        capabilities = listOf(
            // Supports tools, but not parallel/multiple tool-call correlation:
            // LiteRT ToolCall has no stable id, so the LiteRT client currently
            // supports only a single tool call per model response. See
            // LiteRTMessageConverters.toKoogMessages for the runtime guard.
            LLMCapability.Tools,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    /**
     * https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
     */
    public val Gemma4E2B: LLModel = LLModel(
        provider = LiteRTLLMProvider,
        id = "gemma-4-E2B-it.litertlm",
        capabilities = listOf(
            LLMCapability.Tools,
            LLMCapability.Completion,
        ),
        contextLength = 128_000,
    )

    /**
     * https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
     */
    public val Gemma4E4B: LLModel = LLModel(
        provider = LiteRTLLMProvider,
        id = "gemma-4-E4B-it.litertlm",
        capabilities = listOf(
            LLMCapability.Tools,
            LLMCapability.Completion,
        ),
        contextLength = 128_000,
    )

    private val customModels = mutableListOf<LLModel>()

    /** All available models, combining the built-in [FunctionGemma] with any custom models. */
    override val models: List<LLModel>
        get() = listOf(FunctionGemma) + customModels

    /** Registers [model] as an additional on-device model available for inference. */
    override fun addCustomModel(model: LLModel) {
        customModels.add(model)
    }
}
