package ai.koog.agents.features.debugger

import ai.koog.prompt.llm.LLModel

/**
 * A property that combines the provider ID and the model ID of an `LLModel` instance into a single string.
 *
 * It constructs a formatted identifier in the form of `providerId:modelId`, where:
 * - `providerId` is the unique identifier of the `LLMProvider` associated with the model.
 * - `modelId` is the unique identifier for the specific model instance.
 *
 * This property is typically used to uniquely identify an LLM instance for logging, tracing, or serialization purposes.
 */
internal val LLModel.eventString: String
    get() = "${this.provider.id}:${this.id}"
