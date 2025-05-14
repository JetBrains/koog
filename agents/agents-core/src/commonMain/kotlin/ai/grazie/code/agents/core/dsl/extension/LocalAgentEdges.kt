package ai.grazie.code.agents.core.dsl.extension

import ai.grazie.code.agents.core.dsl.builder.LocalAgentEdgeBuilderIntermediate
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.environment.SafeTool
import ai.grazie.code.agents.core.environment.toSafeResult
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.jetbrains.code.prompt.message.Message
import kotlin.reflect.KClass

/**
 * Creates an edge that filters outputs based on their type.
 *
 * @param klass The class to check instance against (not actually used, see implementation comment)
 */
public inline infix fun <IncomingOutput, IntermediateOutput, OutgoingInput, reified T : Any>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onIsInstance(
    /*
     klass is not used, but we need to use this trick to avoid passing all generic parameters on the usage side.
     Removing this parameter and just passing the correct type via generic reified parameter won't work, it requires all
     generic types in this case, which is not nice from the API perspective (trust me, I tried).
     */
    @Suppress("unused")
    klass: KClass<T>
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, T, OutgoingInput> {
    return onCondition { output -> output is T }
        .transformed { it as T }
}


@Suppress("UNCHECKED_CAST")
public inline infix fun <IncomingOutput, OutgoingInput, reified TResult : ToolResult>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, SafeTool.Result<TResult>, OutgoingInput>.onSuccessful(
    crossinline condition: suspend (TResult) -> Boolean
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, SafeTool.Result.Success<TResult>, OutgoingInput> =
    onIsInstance(SafeTool.Result.Success::class).transformed { it as SafeTool.Result.Success<TResult> }
        .onCondition {
            condition(it.result)
        }

@Suppress("UNCHECKED_CAST")
public inline infix fun <IncomingOutput, OutgoingInput, reified TResult : ToolResult>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, SafeTool.Result<TResult>, OutgoingInput>.onFailure(
    crossinline condition: suspend (error: String) -> Boolean
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, SafeTool.Result.Failure<TResult>, OutgoingInput> =
    onIsInstance(SafeTool.Result.Failure::class).transformed { it as SafeTool.Result.Failure<TResult> }
        .onCondition {
            condition(it.message)
        }

/**
 * Creates an edge that filters tool call messages based on a custom condition.
 *
 * @param block A function that evaluates whether to accept a tool call message
 */
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolCall(
    block: suspend (Message.Tool.Call) -> Boolean
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, Message.Tool.Call, OutgoingInput> {
    return onIsInstance(Message.Tool.Call::class)
        .onCondition { toolCall -> block(toolCall) }
}

/**
 * Creates an edge that filters tool call messages for a specific tool and arguments condition.
 *
 * @param tool The tool to match against
 * @param block A function that evaluates the tool arguments to determine if the edge should accept the message
 */
public inline fun <IncomingOutput, IntermediateOutput, OutgoingInput, reified Args : Tool.Args>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolCall(
    tool: Tool<Args, *>,
    crossinline block: suspend (Args) -> Boolean
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, Message.Tool.Call, OutgoingInput> {
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
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolCall(
    tool: Tool<*, *>,
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, Message.Tool.Call, OutgoingInput> {
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
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolNotCalled(
    tool: Tool<*, *>,
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, Message.Tool.Call, OutgoingInput> {
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
public inline fun <IncomingOutput, IntermediateOutput, OutgoingInput, reified Result : ToolResult>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onToolResult(
    tool: Tool<*, Result>,
    crossinline block: suspend (SafeTool.Result<Result>) -> Boolean
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, ReceivedToolResult, OutgoingInput> {
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
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onMultipleToolCalls(
    block: suspend (List<Message.Tool.Call>) -> Boolean
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, List<Message.Tool.Call>, OutgoingInput> {
    return onIsInstance(List::class)
        .transformed { it to it.filterIsInstance<Message.Tool.Call>() }
        .onCondition { (original, filtered) -> original == filtered }
        .transformed { (_, filtered) -> filtered }
        .onCondition { toolCalls -> block(toolCalls) }
}

@Suppress("unused")
/**
 * Creates an edge that filters lists of tool result messages based on a custom condition.
 *
 * @param block A function that evaluates whether to accept a list of tool result messages
 */
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onMultipleToolResults(
    block: suspend (List<ReceivedToolResult>) -> Boolean
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, List<ReceivedToolResult>, OutgoingInput> {
    return onIsInstance(List::class)
        .transformed { it to it.filterIsInstance<ReceivedToolResult>() }
        .onCondition { (original, filtered) -> original == filtered }
        .transformed { (_, filtered) -> filtered }
        .onCondition { toolResults -> block(toolResults) }
}

/**
 * Creates an edge that filters assistant messages based on a custom condition and extracts their content.
 *
 * @param block A function that evaluates whether to accept an assistant message
 */
public infix fun <IncomingOutput, IntermediateOutput, OutgoingInput>
        LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput>.onAssistantMessage(
    block: suspend (Message.Assistant) -> Boolean
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, String, OutgoingInput> {
    return onIsInstance(Message.Assistant::class)
        .onCondition { signature -> block(signature) }
        .transformed { it.content }
}
