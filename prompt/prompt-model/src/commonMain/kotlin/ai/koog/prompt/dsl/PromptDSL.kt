package ai.koog.prompt.dsl

import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock

/**
 * Marker annotation for the Prompt DSL.
 *
 * This annotation is used to create a DSL scope for building prompts.
 */
@DslMarker
public annotation class PromptDSL

/**
 * Creates a new prompt using the Prompt DSL.
 *
 * This function allows creating a prompt with a specific ID and parameters.
 *
 * Example:
 * ```kotlin
 * val prompt = prompt("example-prompt") {
 *     system("You are a helpful assistant.")
 *     user("What is the capital of France?")
 * }
 * ```
 *
 * @param id The identifier for the prompt
 * @param params The parameters for the language model
 * @param clock The clock to use for generating timestamps, defaults to Clock.System
 * @param build The initialization block for the PromptBuilder
 * @return A new Prompt object
 */
@PromptDSL
public fun prompt(
    id: String,
    params: LLMParams = LLMParams(),
    clock: Clock = Clock.System,
    build: PromptBuilder.() -> Unit
): Prompt {
    return Prompt.build(id, params, clock, build)
}

/**
 * Constructs an empty instance of the [Prompt] class with no messages, default parameters,
 * and an empty identifier. This empty prompt can be used as a default or placeholder.
 *
 * @return A new [Prompt] instance with no messages, an empty identifier, and default parameters.
 */
@PromptDSL
public fun emptyPrompt(): Prompt = Prompt.build("") { }

/**
 * Extends an existing prompt using the Prompt DSL.
 *
 * This function allows adding more messages to an existing prompt.
 *
 * Example:
 * ```kotlin
 * val basePrompt = prompt("base-prompt") {
 *     system("You are a helpful assistant.")
 * }
 *
 * val extendedPrompt = prompt(basePrompt) {
 *     user("What is the capital of France?")
 * }
 * ```
 *
 * @param existing The existing prompt to extend
 * @param clock The clock to use for generating timestamps, defaults to Clock.System
 * @param build The initialization block for the PromptBuilder
 * @return A new Prompt object based on the existing one
 */
public fun prompt(
    existing: Prompt,
    clock: Clock = Clock.System,
    build: PromptBuilder.() -> Unit
): Prompt {
    return Prompt.build(existing, clock, build)
}
