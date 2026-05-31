package ai.koog.prompt.executor.clients.foundationmodels

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/** LLM provider identifier for on-device Apple inference via the Foundation Models framework. */
public data object AppleLLMProvider : LLMProvider("apple", "Apple")

/**
 * Catalog of [LLModel] definitions for Apple Foundation Models.
 *
 * Phase-1 advertises only [LLMCapability.Completion] — the only capability the
 * single-shot client honors. (Temperature/streaming/tools come in later phases.)
 * Token counts are advisory metadata; the real window is not exposed by the SDK.
 */
public object AppleLLModels : LLModelDefinitions {
    /** The system on-device model selected by `SystemLanguageModel.default`. */
    public val SystemDefault: LLModel = LLModel(
        provider = AppleLLMProvider,
        id = "system-default",
        capabilities = listOf(LLMCapability.Completion),
        contextLength = 4_096,
        maxOutputTokens = 1_024,
    )

    private val supportedModels: List<LLModel> = listOf(SystemDefault)
    private val customModels = mutableListOf<LLModel>()

    override val models: List<LLModel>
        get() = supportedModels + customModels

    override fun addCustomModel(model: LLModel) {
        require(model.provider == AppleLLMProvider) { "Model provider must be Apple" }
        customModels.add(model)
    }
}
