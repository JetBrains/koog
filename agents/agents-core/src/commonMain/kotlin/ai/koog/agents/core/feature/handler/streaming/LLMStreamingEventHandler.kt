package ai.koog.agents.core.feature.handler.streaming

/**
 * Handler for processing individual stream frames during streaming.
 * This handler is invoked for each frame received from the streaming response.
 */
public class LLMStreamingEventHandler {

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
