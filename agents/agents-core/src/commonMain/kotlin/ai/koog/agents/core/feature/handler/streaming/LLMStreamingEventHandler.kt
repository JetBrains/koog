package ai.koog.agents.core.feature.handler.streaming

/**
 * A handler responsible for managing the streaming flow of Large Language Model (LLM) responses.
 * It allows customization of logic to be executed before streaming starts, during streaming frames,
 * and after streaming completes.
 */
public class LLMStreamingEventHandler {

    /**
     * A handler invoked before streaming from the Language Learning Model (LLM) begins.
     *
     * This handler enables customization or preprocessing steps to be applied before the stream starts.
     * It accepts the prompt, a list of tools, the model, and a run ID as inputs, allowing
     * users to define specific logic or modifications to these inputs before streaming begins.
     */
    public var llmStreamingStartingHandler: LLMStreamingStartingHandler =
        LLMStreamingStartingHandler { _ -> }

    /**
     * A handler invoked when stream frames are sent out during the streaming process.
     *
     * This variable represents a custom implementation of the `StreamFrameHandler` functional interface,
     * allowing real-time processing or custom logic to be performed as each stream frame is received
     * from the LLM.
     *
     * The handler receives the stream frame data and a unique run identifier, enabling real-time
     * monitoring, transformation, or aggregation of streaming content.
     *
     * Customize this handler to implement specific behavior required during the streaming process.
     */
    public var llmStreamingFrameReceivedHandler: LLMStreamingFrameReceivedHandler =
        LLMStreamingFrameReceivedHandler { _ -> }

    /**
     * A handler invoked when an error occurs during streaming from the language model (LLM).
     *
     * This variable represents a custom implementation of the `StreamErrorHandler` functional interface,
     * allowing error handling or logging logic to be applied during streaming errors.
     *
     * The handler receives the error message and a unique run identifier, enabling real-time
     * monitoring or logging of streaming errors.
     *
     * Customize this handler to implement specific behavior required during streaming errors.
     */
    public var llmStreamingFailedHandler: LLMStreamingFailedHandler =
        LLMStreamingFailedHandler { _ -> }

    /**
     * A handler invoked after streaming from the language model (LLM) is complete.
     *
     * This variable represents a custom implementation of the `AfterStreamHandler` functional interface,
     * allowing post-processing or custom logic to be performed once streaming has finished.
     *
     * The handler receives various pieces of information about the stream, including the original prompt,
     * the tools used, the model invoked, and a unique run identifier.
     *
     * Customize this handler to implement specific behavior required after streaming completes.
     */
    public var llmStreamingCompletedHandler: LLMStreamingCompletedHandler =
        LLMStreamingCompletedHandler { _ -> }
}

/**
 * A functional interface implemented to handle logic that occurs before streaming from a large language model (LLM) begins.
 * It allows preprocessing steps or validation based on the provided prompt, available tools, targeted LLM model,
 * and a unique run identifier.
 *
 * This can be particularly useful for custom input manipulation, logging, validation, or applying
 * configurations to the streaming request based on external context.
 */
public fun interface LLMStreamingStartingHandler {
    /**
     * Handles the initialization of a streaming interaction by processing the given prompt, tools, model, and run ID.
     *
     * @param eventContext The context for the before-stream event
     */
    public suspend fun handle(eventContext: LLMStreamingStartingContext)
}

/**
 * A functional interface for handling stream frames as they are received during the streaming process.
 * The implementation of this interface provides a mechanism to perform real-time processing of
 * streaming content, such as aggregation, transformation, or monitoring.
 */
public fun interface LLMStreamingFrameReceivedHandler {
    /**
     * Handles individual stream frames as they are sent out during the streaming process.
     *
     * @param eventContext The context for the stream frame event
     */
    public suspend fun handle(eventContext: LLMStreamingFrameReceivedContext)
}

/**
 * A functional interface for handling streaming errors.
 * The implementation of this interface provides a mechanism to perform error handling or logging
 * based on the provided error message and run ID.
 */
public fun interface LLMStreamingFailedHandler {
    /**
     * Handles streaming errors by processing the provided error message and run ID.
     *
     * @param eventContext The context for the stream error event
     */
    public suspend fun handle(eventContext: LLMStreamingFailedContext)
}

/**
 * Represents a functional interface for handling operations or logic that should occur after streaming
 * from a large language model (LLM) is complete. The implementation of this interface provides a mechanism
 * to perform custom logic or processing based on the provided inputs, such as the prompt, tools,
 * model, and the completion of the stream.
 */
public fun interface LLMStreamingCompletedHandler {
    /**
     * Handles the post-processing of a streaming session and its associated data after streaming completes.
     *
     * @param eventContext The context for the after-stream event
     */
    public suspend fun handle(eventContext: LLMStreamingCompletedContext)
}
