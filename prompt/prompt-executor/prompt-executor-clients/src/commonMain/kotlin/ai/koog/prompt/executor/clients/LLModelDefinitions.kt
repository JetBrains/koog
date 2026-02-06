package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel

/**
 * Interface defining and managing configurations or metadata for supported Large Language Models (LLMs).
 * This serves as a contract for providing LLM-specific definitions, capabilities, and configurations that are
 * needed during interactions with LLM providers. Typically, implementations of this interface represent
 * contextual information about various LLMs.
 */
public interface LLModelDefinitions {
    /**
     * Lists all (both declared and custom) models under this definition
     */
    public val models: List<LLModel>

    /**
     * Adds a custom model to this definition
     * @param model The model to add
     */
    public fun addCustomModel(model: LLModel)
}

/**
 * Maps the model IDs to models under this definition
 */
public fun LLModelDefinitions.modelsById(): Map<String, LLModel> {
    return models.associateBy { it.id }
}
