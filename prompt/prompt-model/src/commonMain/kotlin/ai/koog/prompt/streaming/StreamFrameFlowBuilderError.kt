package ai.koog.prompt.streaming

/**
 * Represents an error that can occur during [buildStreamFrameFlow] operations.
 */
public sealed class StreamFrameFlowBuilderError(message: String) : Throwable(message) {

    /**
     * Occurs when trying to upsert partial tool call data (e.g., parts of the tool arguments contents)
     * when there is no pre-existing partial tool call data to append to.
     *
     * The first partial tool call data for a new tool call should always include the `id`.
     */
    public class NoPartialToolCallToComplete :
        StreamFrameFlowBuilderError("Error constructing tool call, no tool call to complete or no tool call id was provided.")

    /**
     * Occurs when the index of a partial tool call that lacks an `id`
     * does not match the to-be completed tool call's index.
     *
     * @property expectedIndex The expected index of the partial tool call.
     * @property actualIndex The actual index of the partial tool call.
     */
    public class UnexpectedPartialToolCallIndex(expectedIndex: Int, actualIndex: Int) :
        StreamFrameFlowBuilderError("Error constructing tool call, expected index $expectedIndex but was $actualIndex")
}
