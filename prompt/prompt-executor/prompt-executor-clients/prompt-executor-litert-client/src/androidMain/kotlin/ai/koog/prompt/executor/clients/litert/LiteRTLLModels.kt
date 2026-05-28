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
     * Source: https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions
     */
    public val FunctionGemma: LLModel = LLModel(
        provider = LiteRTLLMProvider,
        id = "mobile_actions_q8_ekv1024.litertlm",
        capabilities = listOf(
            LLMCapability.Tools,
            LLMCapability.Completion
        ),
        contextLength = 32_000,
        maxOutputTokens = 1_024,
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
        contextLength = 32_000,
        maxOutputTokens = 4_096,
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
        contextLength = 32_000,
        maxOutputTokens = 4_096,
    )

    /**
     * List of the supported models by the LiteRT provider.
     */
    private val supportedModels: List<LLModel> = listOf(
        FunctionGemma,
        Gemma4E2B,
        Gemma4E4B
    )

    private val customModels = mutableListOf<LLModel>()

    /** All available models, combining the built-in [FunctionGemma] with any custom models. */
    override val models: List<LLModel>
        get() = supportedModels + customModels

    /** Registers [model] as an additional on-device model available for inference. */
    override fun addCustomModel(model: LLModel) {
        require(model.provider == LiteRTLLMProvider) { "Model provider must be LiteRT" }
        customModels.add(model)
    }
}
