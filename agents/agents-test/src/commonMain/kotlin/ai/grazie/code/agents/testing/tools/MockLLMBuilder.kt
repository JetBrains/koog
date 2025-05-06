package ai.grazie.code.agents.testing.tools

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolResult
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message

class ToolCondition<Args : Tool.Args, Result : ToolResult>(
    val tool: Tool<Args, Result>,
    val argsCondition: suspend (Args) -> Boolean,
    val produceResult: suspend (Args) -> Result
) {
    internal suspend fun satisfies(toolCall: Message.Tool.Call) =
        tool.name == toolCall.tool && argsCondition(tool.decodeArgsFromString(toolCall.content))

    internal suspend fun invoke(toolCall: Message.Tool.Call) =
        produceResult(tool.decodeArgsFromString(toolCall.content))

    internal suspend fun invokeAndSerialize(toolCall: Message.Tool.Call): Pair<Result, String> {
        val toolResult = produceResult(tool.decodeArgsFromString(toolCall.content))
        return toolResult to tool.encodeResultToString(toolResult)
    }
}

class MockLLMBuilder {
    private val assistantPartialMatches = mutableMapOf<String, String>()
    private val assistantExactMatches = mutableMapOf<String, String>()
    private val conditional = mutableMapOf<(String) -> Boolean, String>()
    private val toolCallExactMatches = mutableMapOf<String, Message.Tool.Call>()
    private val toolCallPartialMatches = mutableMapOf<String, Message.Tool.Call>()
    private var defaultResponse: String = ""
    private var toolRegistry: ToolRegistry? = null
    private var eventHandler: EventHandler? = null
    private var toolActions: MutableList<ToolCondition<*, *>> = mutableListOf()

    companion object {
        var currentBuilder: MockLLMBuilder? = null
    }

    fun setDefaultResponse(response: String) {
        defaultResponse = response
    }

    fun setToolRegistry(registry: ToolRegistry) {
        toolRegistry = registry
    }

    fun setEventHandler(handler: EventHandler) {
        eventHandler = handler
    }

    fun <Args : Tool.Args> addLLMAnswerExactPattern(llmAnswer: String, tool: Tool<Args, *>, args: Args) {
        toolCallExactMatches[llmAnswer] =
            Message.Tool.Call(id = null, tool = tool.name, content = tool.encodeArgsToString(args))
    }

    fun <Args : Tool.Args, Result : ToolResult> addToolAction(
        tool: Tool<Args, Result>,
        argsCondition: suspend (Args) -> Boolean = { true },
        action: suspend (Args) -> Result
    ) {
        toolActions += ToolCondition(tool, argsCondition, action)
    }

    fun <Args : Tool.Args> mockLLMToolCall(tool: Tool<Args, *>, args: Args): ToolCallReceiver<Args> {
        return ToolCallReceiver(tool, args, this)
    }

    fun <Args : Tool.Args, Result : ToolResult> mockTool(tool: Tool<Args, Result>): MockToolReceiver<Args, Result> {
        return MockToolReceiver(tool, this)
    }

    infix fun String.onUserRequestContains(pattern: String): MockLLMBuilder {
        assistantPartialMatches[pattern] = this
        return this@MockLLMBuilder
    }

    infix fun String.onUserRequestEquals(pattern: String): MockLLMBuilder {
        assistantExactMatches[pattern] = this
        return this@MockLLMBuilder
    }

    infix fun String.onCondition(condition: (String) -> Boolean): MockLLMBuilder {
        conditional[condition] = this
        return this@MockLLMBuilder
    }

    class ToolCallReceiver<Args : Tool.Args>(
        private val tool: Tool<Args, *>,
        private val args: Args,
        private val builder: MockLLMBuilder
    ) {
        infix fun onRequestEquals(llmAnswer: String): String {
            // Using the llmAnswer directly as the response, which should contain the tool call JSON
            builder.addLLMAnswerExactPattern(llmAnswer, tool, args)

            // Return the llmAnswer as is, which should be a valid tool call JSON
            return llmAnswer
        }
    }

    class MockToolReceiver<Args : Tool.Args, Result : ToolResult>(
        private val tool: Tool<Args, Result>,
        private val builder: MockLLMBuilder
    ) {
        class MockToolResponseBuilder<Args : Tool.Args, Result : ToolResult>(
            private val tool: Tool<Args, Result>,
            private val action: suspend () -> Result,
            private val builder: MockLLMBuilder
        ) {
            infix fun onArguments(args: Args) {
                builder.addToolAction(tool, { it == args }) { action() }
            }

            infix fun onArgumentsMatching(condition: suspend (Args) -> Boolean) {
                builder.addToolAction(tool, condition) { action() }
            }
        }

        infix fun alwaysReturns(response: Result) {
            builder.addToolAction(tool) { response }
        }

        infix fun alwaysDoes(action: suspend () -> Result) {
            builder.addToolAction(tool) { action() }
        }

        infix fun returns(result: Result): MockToolResponseBuilder<Args, Result> =
            MockToolResponseBuilder(tool, { result }, builder)

        infix fun does(action: suspend () -> Result): MockToolResponseBuilder<Args, Result> =
            MockToolResponseBuilder(tool, action, builder)
    }

    infix fun <Args : Tool.Args> MockToolReceiver<Args, ToolResult.Text>.alwaysReturns(response: String) =
        alwaysReturns(ToolResult.Text(response))

    infix fun <Args : Tool.Args> MockToolReceiver<Args, ToolResult.Text>.alwaysTells(action: suspend () -> String) =
        alwaysDoes { ToolResult.Text(action()) }

    infix fun <Args : Tool.Args> MockToolReceiver<Args, ToolResult.Text>.doesStr(action: suspend () -> String) =
        does { ToolResult.Text(action()) }

    fun build(): PromptExecutor {
        val combinedExactMatches = assistantExactMatches.mapValues {
            Message.Assistant(it.value.trimIndent())
        } + toolCallExactMatches
        val combinedPartialMatches = assistantPartialMatches.mapValues {
            Message.Assistant(it.value.trimIndent())
        } + toolCallPartialMatches

        return MockLLMExecutor(
            partialMatches = combinedPartialMatches.takeIf { it.isNotEmpty() },
            exactMatches = combinedExactMatches.takeIf { it.isNotEmpty() },
            conditional = conditional.takeIf { it.isNotEmpty() },
            defaultResponse = defaultResponse,
            eventHandler = eventHandler,
            toolRegistry = toolRegistry,
            toolActions = toolActions
        )
    }
}

fun mockLLMAnswer(response: String) = DefaultResponseReceiver(response)
class DefaultResponseReceiver(val response: String) {
    companion object {
        private var defaultResponse: String? = null
        private val partialMatches = mutableMapOf<String, String>()
        private val exactMatches = mutableMapOf<String, String>()
        private val conditionalMatches = mutableMapOf<(String) -> Boolean, String>()

        fun getDefaultResponse(): String? {
            return defaultResponse
        }

        fun getPartialMatches(): Map<String, String> {
            return partialMatches
        }

        fun getExactMatches(): Map<String, String> {
            return exactMatches
        }

        fun getConditionalMatches(): Map<(String) -> Boolean, String> {
            return conditionalMatches
        }

        fun clearMatches() {
            partialMatches.clear()
            exactMatches.clear()
            conditionalMatches.clear()
            defaultResponse = null
        }
    }

    val asDefaultResponse: String
        get() {
            defaultResponse = response
            return response
        }

    infix fun onRequestContains(pattern: String): String {
        partialMatches[pattern] = response
        return response
    }

    infix fun onRequestEquals(pattern: String): String {
        exactMatches[pattern] = response
        return response
    }

    infix fun onCondition(condition: (String) -> Boolean): String {
        conditionalMatches[condition] = response
        return response
    }
}

fun getMockExecutor(
    toolRegistry: ToolRegistry? = null,
    eventHandler: EventHandler? = null,
    init: MockLLMBuilder.() -> Unit
): PromptExecutor {

    // Clear previous matches
    DefaultResponseReceiver.clearMatches()

    // Call MockLLMBuilder and apply toolRegistry, eventHandler and set currentBuilder to this (to add mocked tool calls)
    val builder = MockLLMBuilder().apply {
        toolRegistry?.let { setToolRegistry(it) }
        eventHandler?.let { setEventHandler(it) }
        MockLLMBuilder.currentBuilder = this
        init()
        MockLLMBuilder.currentBuilder = null
    }

    // Apply stored responses from DefaultResponseReceiver
    DefaultResponseReceiver.getDefaultResponse()?.let { builder.setDefaultResponse(it) }

    // Add partial matches from DefaultResponseReceiver
    DefaultResponseReceiver.getPartialMatches().forEach { (pattern, response) ->
        builder.apply { response.onUserRequestContains(pattern) }
    }

    // Add exact matches from DefaultResponseReceiver
    DefaultResponseReceiver.getExactMatches().forEach { (pattern, response) ->
        builder.apply { response.onUserRequestEquals(pattern) }
    }

    // Add conditional matches from DefaultResponseReceiver
    DefaultResponseReceiver.getConditionalMatches().forEach { (condition, response) ->
        builder.apply { response.onCondition(condition) }
    }

    return builder.build()
}
