package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilderIntermediate
import ai.koog.agents.core.dsl.builder.EdgeTransformationDslMarker
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.toSafeResult
import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import kotlin.reflect.KClass

/**
 * Creates an edge that filters outputs based on their type.
 *
 * @param klass The class to check instance against (not actually used, see implementation comment)
 */
@EdgeTransformationDslMarker
public inline infix fun <IncomingOutput, IntermediateOutput, OutgoingInput, reified T : Any> AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onIsInstance(
    /*
     klass is not used, but we need to use this trick to avoid passing all generic parameters on the usage side.
     Removing this parameter and just passing the correct type via generic reified parameter won't work, it requires all
     generic types in this case, which is not nice from the API perspective (trust me, I tried).
     */
    @Suppress("unused")
    klass: KClass<T>
): AIAgentEdgeBuilderIntermediate<IncomingOutput, T, OutgoingInput> {
    return onCondition { output -> output is T }
        .transformed { it as T }
}

/**
 * Filters and transforms the intermediate outputs of the AI agent node based on the success results of a tool operation.
 *
 * This method is used to create a conditional path in the agent's execution by selecting only the successful results
 * of type [SafeTool.Result.Success] and evaluating them against a provided condition.
 *
 * @param condition A suspending lambda function that accepts a result of type [TResult]
 *                  and evaluates it to a Boolean value. Returns `true` if the condition is satisfied,
 *                  and `false` otherwise.
 * @return An instance of [AIAgentEdgeBuilderIntermediate] configured to handle only successful tool results
 *         that satisfy the specified condition, with output type adjusted to [SafeTool.Result.Success].
 */
@Suppress("UNCHECKED_CAST")
@EdgeTransformationDslMarker
public inline infix fun <IncomingOutput, OutgoingInput, reified TResult> AIAgentEdgeBuilderIntermediate<IncomingOutput, SafeTool.Result<TResult>, OutgoingInput>.onSuccessful(
    crossinline condition: suspend (TResult) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, SafeTool.Result.Success<TResult>, OutgoingInput> =
    onIsInstance(SafeTool.Result.Success::class).transformed { it as SafeTool.Result.Success<TResult> }
        .onCondition {
            condition(it.result)
        }

/**
 * Defines a handler to process failure cases in a directed edge strategy by applying a condition
 * to filter intermediate results of type `SafeTool.Result.Failure`. This method is used to specialize
 * processing for failure results and to propagate or transform them based on the provided condition.
 *
 * @param condition A suspending lambda function that takes an error message string as input and returns a boolean.
 *                  It specifies whether the error should be further processed based on the condition provided.
 * @return A new instance of `AIAgentEdgeBuilderIntermediate`, where the intermediate output type is restricted
 *         to `SafeTool.Result.Failure` containing the specified `TResult` for failure results that match the condition.
 */
@Suppress("UNCHECKED_CAST")
@EdgeTransformationDslMarker
public inline infix fun <IncomingOutput, OutgoingInput, reified TResult> AIAgentEdgeBuilderIntermediate<IncomingOutput, SafeTool.Result<TResult>, OutgoingInput>.onFailure(
    crossinline condition: suspend (error: String) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, SafeTool.Result.Failure<TResult>, OutgoingInput> =
    onIsInstance(SafeTool.Result.Failure::class).transformed { it as SafeTool.Result.Failure<TResult> }
        .onCondition {
            condition(it.message)
        }

/**
 * Creates an edge that filters tool call messages based on a custom condition.
 *
 * @param block A function that evaluates whether to accept a tool call message
 */
@EdgeTransformationDslMarker
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput> AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolCall(
    block: suspend (Message.Tool.Call) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, Message.Tool.Call, OutgoingInput> {
    return onIsInstance(Message.Tool.Call::class)
        .onCondition { toolCall -> block(toolCall) }
}

/**
 * Creates an edge that filters tool call messages for a specific tool and arguments condition.
 *
 * @param tool The tool to match against
 * @param block A function that evaluates the tool arguments to determine if the edge should accept the message
 */
@EdgeTransformationDslMarker
public inline fun <IncomingOutput, IntermediateOutput, OutgoingInput, reified Args> AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolCall(
    tool: Tool<Args, *>,
    crossinline block: suspend (Args) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, Message.Tool.Call, OutgoingInput> {
    return onIsInstance(Message.Tool.Call::class)
        .onCondition { it.tool == tool.name }
        .onCondition { toolCall ->
            val args = tool.decodeArgs(toolCall.contentJson)
            block(args)
        }
}

/**
 * Creates an edge that filters tool call messages for a specific tool.
 *
 * @param tool The tool to match against
 */
@EdgeTransformationDslMarker
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput> AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolCall(
    tool: Tool<*, *>,
): AIAgentEdgeBuilderIntermediate<IncomingOutput, Message.Tool.Call, OutgoingInput> {
    return onIsInstance(Message.Tool.Call::class)
        .onCondition {
            it.tool == tool.name
        }
}

/**
 * Creates an edge that filters tool call messages to NOT be a specific tool
 *
 * @param tool The tool to match against
 */
@EdgeTransformationDslMarker
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput> AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolNotCalled(
    tool: Tool<*, *>,
): AIAgentEdgeBuilderIntermediate<IncomingOutput, Message.Tool.Call, OutgoingInput> {
    return onIsInstance(Message.Tool.Call::class)
        .onCondition {
            it.tool != tool.name
        }
}

/**
 * Creates an edge that filters tool result messages for a specific tool and result condition.
 *
 * @param tool The tool to match against
 * @param block A function that evaluates the tool result to determine if the edge should accept the message
 */
@EdgeTransformationDslMarker
public inline fun <IncomingOutput, IntermediateOutput, OutgoingInput, reified Result> AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolResult(
    tool: Tool<*, Result>,
    crossinline block: suspend (SafeTool.Result<Result>) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, ReceivedToolResult, OutgoingInput> {
    return onIsInstance(ReceivedToolResult::class)
        .onCondition { toolResult ->
            (toolResult.tool == tool.name) && block(toolResult.toSafeResult())
        }
}

/**
 * Creates an edge that filters lists of tool call messages based on a custom condition.
 *
 * @param block A function that evaluates whether to accept a list of tool call messages
 */
@EdgeTransformationDslMarker
public infix fun <IncomingOutput, OutgoingInput> AIAgentEdgeBuilderIntermediate<IncomingOutput, List<Message.Response>, OutgoingInput>.onMultipleToolCalls(
    block: suspend (List<Message.Tool.Call>) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, List<Message.Tool.Call>, OutgoingInput> {
    return onIsInstance(List::class)
        .transformed { it.filterIsInstance<Message.Tool.Call>() }
        // skipping this edge in case we have list of only assistant messages
        .onCondition { it.any() }
        .onCondition { toolCalls -> block(toolCalls) }
}

/**
 * Creates an edge that filters lists of tool result messages based on a custom condition.
 *
 * @param block A function that evaluates whether to accept a list of tool result messages
 */
@Suppress("unused")
@EdgeTransformationDslMarker
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput> AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onMultipleToolResults(
    block: suspend (List<ReceivedToolResult>) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, List<ReceivedToolResult>, OutgoingInput> {
    return onIsInstance(List::class)
        .transformed { it.filterIsInstance<ReceivedToolResult>() }
        .onCondition { toolResults -> block(toolResults) }
}

/**
 * Creates an edge that filters assistant messages based on a custom condition and extracts their content.
 *
 * @param block A function that evaluates whether to accept an assistant message
 */
@EdgeTransformationDslMarker
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput> AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onAssistantMessage(
    block: suspend (Message.Assistant) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, String, OutgoingInput> {
    return onIsInstance(Message.Assistant::class)
        .onCondition { signature -> block(signature) }
        .transformed { it.content }
}

/**
 * Creates an edge that filters assistant messages based on a custom condition and extracts their content.
 *
 * @param block A function that evaluates whether to accept an assistant message
 */
@EdgeTransformationDslMarker
public infix fun <IncomingOutput, OutgoingInput> AIAgentEdgeBuilderIntermediate<IncomingOutput, List<Message.Response>, OutgoingInput>.onMultipleAssistantMessages(
    block: suspend (List<Message.Assistant>) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, List<Message.Assistant>, OutgoingInput> {
    return onIsInstance(List::class)
        .transformed { it.filterIsInstance<Message.Assistant>() }
        .onCondition { toolResults -> block(toolResults) }
}

/**
 * Creates an edge that filters assistant messages based on a custom condition and provides access to media content.
 *
 * @param block A function that evaluates whether to accept an assistant message with media
 */
@EdgeTransformationDslMarker
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput> AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onAssistantMessageWithMedia(
    block: suspend (Message.Assistant) -> Boolean
): AIAgentEdgeBuilderIntermediate<IncomingOutput, List<ContentPart.Attachment>, OutgoingInput> {
    return onIsInstance(Message.Assistant::class)
        .onCondition {
            it.hasAttachments()
        }
        .onCondition { signature -> block(signature) }
        .transformed { it.parts.filterIsInstance<ContentPart.Attachment>() }
}
