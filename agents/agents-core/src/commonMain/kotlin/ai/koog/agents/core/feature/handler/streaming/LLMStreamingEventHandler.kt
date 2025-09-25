package ai.koog.agents.core.feature.handler.streaming

/**
 * Handler for processing individual stream frames during streaming.
 * This handler is invoked for each frame received from the streaming response.
 * A handler responsible for managing the streaming flow of Large Language Model (LLM) responses.
 * It allows customization of logic to be executed before streaming starts, during streaming frames,
 * and after streaming completes.
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
     * Handles a stream frame event.
     * @param eventContext The context containing the stream frame data
     */
    public suspend fun handle(eventContext: LLMStreamingFrameReceivedContext)
}

/**
 * Handler for processing errors that occur during streaming.
 * This handler is invoked when an error occurs in the streaming flow.
 */
public fun interface LLMStreamingFailedHandler {
    /**
     * Handles a stream error event.
     * @param eventContext The context containing the error information
     */
    public suspend fun handle(eventContext: LLMStreamingFailedContext)
}
