package ai.koog.ktor.utils

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.executor.llms.getModelFromIdentifier as getModelFromIdentifierImpl

/**
 * Gets a model from a string identifier in the format "provider.category.model" or "provider.model".
 * For example, "openai.chat.gpt4o" would resolve to OpenAIModels.Chat.GPT4o.
 *
 * @param identifier The string identifier of the model.
 * @param delimiter The delimiter used to separate parts of the identifier. Defaults to ".".
 * @return The resolved LLModel or null if the model cannot be resolved.
 */
@Deprecated(
    message = "Moved to ai.koog.prompt.executor.llms package",
    replaceWith = ReplaceWith(
        "getModelFromIdentifier(identifier, delimiter)",
        "ai.koog.prompt.executor.llms.getModelFromIdentifier"
    )
)
public fun getModelFromIdentifier(identifier: String, delimiter: String = "."): LLModel? {
    return getModelFromIdentifierImpl(identifier, delimiter)
}
