package ai.koog.prompt.streaming

/**
 * Represents an error that can occur during [buildStreamFrameFlow] operations.
 */
public sealed class StreamFrameFlowBuilderError(message: String) : Throwable(message) {

    /**
     * Represents an error that can occur during [StreamFrameFlowBuilder.upsertToolCall].
     */
    public object NoPendingToolCall
        : StreamFrameFlowBuilderError("No tool call is in progress, and no tool call id was provided.")

    /**
     * Represents an error that can occur during [StreamFrameFlowBuilder.upsertToolCall].
     */
    public class ToolCallIndexMismatch(expected: Int, actual: Int) :
        StreamFrameFlowBuilderError("Tool call index mismatch. Expected $expected, got $actual.")
}
