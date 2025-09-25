package ai.koog.agents.core.feature.handler.llm

/**
 * A handler responsible for managing the execution flow of a Large Language Model (LLM) call.
 * It allows customization of logic to be executed before and after the LLM is called.
 */
public class LLMCallEventHandler {

    /**
     * A handler that is invoked before making a call to the Language Learning Model (LLM).
     *
     * This handler enables customization or preprocessing steps to be applied before querying the model.
     * It accepts the prompt, a list of tools, the model, and a session UUID as inputs, allowing
     * users to define specific logic or modifications to these inputs before the call is made.
     */
    public var llmCallStartingHandler: LLMCallStartingHandler =
        LLMCallStartingHandler { _ -> }

    /**
     * A handler invoked after a call to a language model (LLM) is executed.
     *
     * This variable represents a custom implementation of the `AfterLLMCallHandler` functional interface,
     * allowing post-processing or custom logic to be performed once the LLM has returned a response.
     *
     * The handler receives various pieces of information about the LLM call, including the original prompt,
     * the tools used, the model invoked, the responses returned by the model, and a unique run identifier.
     *
     * Customize this handler to implement specific behavior required immediately after LLM processing.
     */
    public var llmCallCompletedHandler: LLMCallCompletedHandler =
        LLMCallCompletedHandler { _ -> }
}

/**
 * A functional interface implemented to handle logic that occurs before invoking a large language model (LLM).
 * It allows preprocessing steps or validation based on the provided prompt, available tools, targeted LLM model,
 * and a unique run identifier.
 *
 * This can be particularly useful for custom input manipulation, logging, validation, or applying
 * configurations to the LLM request based on external context.
 */
public fun interface LLMCallStartingHandler {
    /**
     * Handles a language model interaction by processing the given prompt, tools, model, and sess
     *
     * @param eventContext The context for the event
     */
    public suspend fun handle(eventContext: LLMCallStartingContext)
}

/**
 * Represents a functional interface for handling operations or logic that should occur after a call
 * to a large language model (LLM) is made. The implementation of this interface provides a mechanism
 * to perform custom logic or processing based on the provided inputs, such as the prompt, tools,
 * model, and generated responses.
 */
public fun interface LLMCallCompletedHandler {
    /**
     * Handles the post-processing of a prompt and its associated data after a language model call.
     *
     * @param eventContext The context for the event
     */
    public suspend fun handle(eventContext: LLMCallCompletedContext)
}
