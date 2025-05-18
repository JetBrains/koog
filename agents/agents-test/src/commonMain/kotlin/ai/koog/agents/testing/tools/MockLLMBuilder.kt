package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message

/**
 * Represents a condition for a tool call and its corresponding result.
 *
 * This class is used to define how a tool should respond to specific inputs during testing.
 * It encapsulates the tool, a condition to check if the tool call matches, and a function
 * to produce the result when the condition is satisfied.
 *
 * @param Args The type of arguments the tool accepts
 * @param Result The type of result the tool produces
 * @property tool The tool to be mocked
 * @property argsCondition A function that determines if the tool call matches this condition
 * @property produceResult A function that produces the result when the condition is satisfied
 */
public class ToolCondition<Args : Tool.Args, Result : ToolResult>(
    public val tool: Tool<Args, Result>,
    public val argsCondition: suspend (Args) -> Boolean,
    public val produceResult: suspend (Args) -> Result
) {
    /**
     * Checks if this condition applies to the given tool call.
     *
     * @param toolCall The tool call to check
     * @return True if the tool name matches and the arguments satisfy the condition
     */
    internal suspend fun satisfies(toolCall: Message.Tool.Call) =
        tool.name == toolCall.tool && argsCondition(tool.decodeArgsFromString(toolCall.content))

    /**
     * Invokes the tool with the arguments from the tool call.
     *
     * @param toolCall The tool call containing the arguments
     * @return The result produced by the tool
     */
    internal suspend fun invoke(toolCall: Message.Tool.Call) =
        produceResult(tool.decodeArgsFromString(toolCall.content))

    /**
     * Invokes the tool and serializes the result.
     *
     * @param toolCall The tool call containing the arguments
     * @return A pair of the result object and its serialized string representation
     */
    internal suspend fun invokeAndSerialize(toolCall: Message.Tool.Call): Pair<Result, String> {
        val toolResult = produceResult(tool.decodeArgsFromString(toolCall.content))
        return toolResult to tool.encodeResultToString(toolResult)
    }
}

/**
 * Builder class for creating mock LLM executors for testing.
 *
 * This class provides a fluent API for configuring mock responses for LLM requests and tool calls.
 * It allows you to define how the LLM should respond to different inputs and how tools should
 * behave when called during testing.
 *
 * Example usage:
 * ```kotlin
 * val mockLLMApi = getMockExecutor(toolRegistry) {
 *     // Mock LLM text responses
 *     mockLLMAnswer("Hello!") onRequestContains "Hello"
 *     mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
 *
 *     // Mock LLM tool calls
 *     mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
 *
 *     // Mock tool behavior
 *     mockTool(PositiveToneTool) alwaysReturns "The text has a positive tone."
 *     mockTool(NegativeToneTool) alwaysTells {
 *         println("Negative tone tool called")
 *         "The text has a negative tone."
 *     }
 * }
 * ```
 */
public class MockLLMBuilder {
    private val assistantPartialMatches = mutableMapOf<String, String>()
    private val assistantExactMatches = mutableMapOf<String, String>()
    private val conditional = mutableMapOf<(String) -> Boolean, String>()
    private val toolCallExactMatches = mutableMapOf<String, Message.Tool.Call>()
    private val toolCallPartialMatches = mutableMapOf<String, Message.Tool.Call>()
    private var defaultResponse: String = ""
    private var toolRegistry: ToolRegistry? = null
    private var toolActions: MutableList<ToolCondition<*, *>> = mutableListOf()

    /**
     * Companion object for the MockLLMBuilder class.
     * Provides access to the current builder instance during configuration.
     */
    internal companion object {
        var currentBuilder: MockLLMBuilder? = null
    }

    /**
     * Sets the default response to be returned when no other response matches.
     *
     * @param response The default response string
     */
    public fun setDefaultResponse(response: String) {
        defaultResponse = response
    }

    /**
     * Sets the tool registry to be used for tool execution.
     *
     * @param registry The tool registry containing all available tools
     */
    public fun setToolRegistry(registry: ToolRegistry) {
        toolRegistry = registry
    }

    /**
     * Adds an exact pattern match for an LLM answer that triggers a tool call.
     *
     * @param llmAnswer The exact input string to match
     * @param tool The tool to be called when the input matches
     * @param args The arguments to pass to the tool
     */
    public fun <Args : Tool.Args> addLLMAnswerExactPattern(llmAnswer: String, tool: Tool<Args, *>, args: Args) {
        toolCallExactMatches[llmAnswer] =
            Message.Tool.Call(id = null, tool = tool.name, content = tool.encodeArgsToString(args))
    }

    /**
     * Adds a tool action to be executed when a tool call matches the specified condition.
     *
     * @param tool The tool to be mocked
     * @param argsCondition A function that determines if the tool call arguments match this action
     * @param action A function that produces the result when the condition is satisfied
     */
    public fun <Args : Tool.Args, Result : ToolResult> addToolAction(
        tool: Tool<Args, Result>,
        argsCondition: suspend (Args) -> Boolean = { true },
        action: suspend (Args) -> Result
    ) {
        toolActions += ToolCondition(tool, argsCondition, action)
    }

    /**
     * Creates a mock for an LLM tool call.
     *
     * This method is used to define how the LLM should respond with a tool call
     * when it receives a specific input.
     *
     * @param tool The tool to be called
     * @param args The arguments to pass to the tool
     * @return A [ToolCallReceiver] for further configuration
     */
    public fun <Args : Tool.Args> mockLLMToolCall(tool: Tool<Args, *>, args: Args): ToolCallReceiver<Args> {
        return ToolCallReceiver(tool, args, this)
    }

    /**
     * Creates a mock for a tool.
     *
     * This method is used to define how a tool should behave when it is called
     * during testing.
     *
     * @param tool The tool to be mocked
     * @return A [MockToolReceiver] for further configuration
     */
    public fun <Args : Tool.Args, Result : ToolResult> mockTool(tool: Tool<Args, Result>): MockToolReceiver<Args, Result> {
        return MockToolReceiver(tool, this)
    }

    /**
     * Configures the LLM to respond with this string when the user request contains the specified pattern.
     *
     * @param pattern The substring to look for in the user request
     * @return The MockLLMBuilder instance for method chaining
     */
    public infix fun String.onUserRequestContains(pattern: String): MockLLMBuilder {
        assistantPartialMatches[pattern] = this
        return this@MockLLMBuilder
    }

    /**
     * Configures the LLM to respond with this string when the user request exactly matches the specified pattern.
     *
     * @param pattern The exact string to match in the user request
     * @return The MockLLMBuilder instance for method chaining
     */
    public infix fun String.onUserRequestEquals(pattern: String): MockLLMBuilder {
        assistantExactMatches[pattern] = this
        return this@MockLLMBuilder
    }

    /**
     * Configures the LLM to respond with this string when the user request satisfies the specified condition.
     *
     * @param condition A function that evaluates the user request and returns true if it matches
     * @return The MockLLMBuilder instance for method chaining
     */
    public infix fun String.onCondition(condition: (String) -> Boolean): MockLLMBuilder {
        conditional[condition] = this
        return this@MockLLMBuilder
    }

    /**
     * Receiver class for configuring tool call responses from the LLM.
     *
     * This class is part of the fluent API for configuring how the LLM should respond
     * with tool calls when it receives specific inputs.
     *
     * @param Args The type of arguments the tool accepts
     * @property tool The tool to be called
     * @property args The arguments to pass to the tool
     * @property builder The parent MockLLMBuilder instance
     */
    public class ToolCallReceiver<Args : Tool.Args>(
        private val tool: Tool<Args, *>,
        private val args: Args,
        private val builder: MockLLMBuilder
    ) {
        /**
         * Configures the LLM to respond with a tool call when the user request exactly matches the specified pattern.
         *
         * @param llmAnswer The exact string to match in the user request
         * @return The llmAnswer string for method chaining
         */
        public infix fun onRequestEquals(llmAnswer: String): String {
            // Using the llmAnswer directly as the response, which should contain the tool call JSON
            builder.addLLMAnswerExactPattern(llmAnswer, tool, args)

            // Return the llmAnswer as is, which should be a valid tool call JSON
            return llmAnswer
        }
    }

    /**
     * Receiver class for configuring tool behavior during testing.
     *
     * This class is part of the fluent API for configuring how tools should behave
     * when they are called during testing.
     *
     * @param Args The type of arguments the tool accepts
     * @param Result The type of result the tool produces
     * @property tool The tool to be mocked
     * @property builder The parent MockLLMBuilder instance
     */
    public class MockToolReceiver<Args : Tool.Args, Result : ToolResult>(
        internal val tool: Tool<Args, Result>,
        internal val builder: MockLLMBuilder
    ) {
        /**
         * Builder class for configuring conditional tool responses.
         *
         * This class allows you to specify when a tool should return a particular result
         * based on the arguments it receives.
         *
         * @param Args The type of arguments the tool accepts
         * @param Result The type of result the tool produces
         * @property tool The tool to be mocked
         * @property action A function that produces the result
         * @property builder The parent MockLLMBuilder instance
         */
        public class MockToolResponseBuilder<Args : Tool.Args, Result : ToolResult>(
            private val tool: Tool<Args, Result>,
            private val action: suspend () -> Result,
            private val builder: MockLLMBuilder
        ) {
            /**
             * Configures the tool to return the specified result when it receives exactly the specified arguments.
             *
             * @param args The exact arguments to match
             */
            public infix fun onArguments(args: Args) {
                builder.addToolAction(tool, { it == args }) { action() }
            }

            /**
             * Configures the tool to return the specified result when it receives arguments that satisfy the specified condition.
             *
             * @param condition A function that evaluates the arguments and returns true if they match
             */
            public infix fun onArgumentsMatching(condition: suspend (Args) -> Boolean) {
                builder.addToolAction(tool, condition) { action() }
            }
        }

        /**
         * Configures the tool to always return the specified result, regardless of the arguments it receives.
         *
         * @param response The result to return
         */
        public infix fun alwaysReturns(response: Result) {
            builder.addToolAction(tool) { response }
        }

        /**
         * Configures the tool to always execute the specified action, regardless of the arguments it receives.
         *
         * @param action A function that produces the result
         */
        public infix fun alwaysDoes(action: suspend () -> Result) {
            builder.addToolAction(tool) { action() }
        }

        /**
         * Configures the tool to return the specified result when it receives matching arguments.
         *
         * @param result The result to return
         * @return A [MockToolResponseBuilder] for further configuration
         */
        public infix fun returns(result: Result): MockToolResponseBuilder<Args, Result> =
            MockToolResponseBuilder(tool, { result }, builder)

        /**
         * Configures the tool to execute the specified action when it receives matching arguments.
         *
         * @param action A function that produces the result
         * @return A [MockToolResponseBuilder] for further configuration
         */
        public infix fun does(action: suspend () -> Result): MockToolResponseBuilder<Args, Result> =
            MockToolResponseBuilder(tool, action, builder)
    }

    /**
     * Convenience extension function for configuring a text tool to always return the specified string.
     *
     * @param response The string to return
     * @return The result of the alwaysReturns call
     */
    public infix fun <Args : Tool.Args> MockToolReceiver<Args, ToolResult.Text>.alwaysReturns(response: String): Unit =
        alwaysReturns(ToolResult.Text(response))

    /**
     * Convenience extension function for configuring a text tool to always execute the specified action
     * and return its string result.
     *
     * @param action A function that produces the string result
     * @return The result of the alwaysDoes call
     */
    public infix fun <Args : Tool.Args> MockToolReceiver<Args, ToolResult.Text>.alwaysTells(action: suspend () -> String): Unit =
        alwaysDoes { ToolResult.Text(action()) }

    /**
     * Convenience extension function for configuring a text tool to execute the specified action
     * and return its string result when it receives matching arguments.
     *
     * @param action A function that produces the string result
     * @return The result of the does call
     */
    public infix fun <Args : Tool.Args> MockToolReceiver<Args, ToolResult.Text>.doesStr(action: suspend () -> String):  MockLLMBuilder.MockToolReceiver.MockToolResponseBuilder<Args, ToolResult.Text> =
        does { ToolResult.Text(action()) }

    /**
     * Builds and returns a PromptExecutor configured with the mock responses and tool actions.
     *
     * This method combines all the configured responses and tool actions into a MockLLMExecutor
     * that can be used for testing.
     *
     * @return A configured MockLLMExecutor instance
     */
    public fun build(): PromptExecutor {
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
            toolRegistry = toolRegistry,
            toolActions = toolActions
        )
    }
}

/**
 * Creates a mock LLM text response.
 *
 * This function is the entry point for configuring how the LLM should respond with text
 * when it receives specific inputs.
 *
 * @param response The text response to return
 * @return A [DefaultResponseReceiver] for further configuration
 *
 * Example usage:
 * ```kotlin
 * // Mock a simple text response
 * mockLLMAnswer("Hello!") onRequestContains "Hello"
 *
 * // Mock a default response
 * mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
 * ```
 */
public fun mockLLMAnswer(response: String): DefaultResponseReceiver = DefaultResponseReceiver(response)

/**
 * Receiver class for configuring text responses from the LLM.
 *
 * This class is part of the fluent API for configuring how the LLM should respond
 * with text when it receives specific inputs.
 *
 * @property response The text response to return
 */
public class DefaultResponseReceiver(public val response: String) {
    /**
     * Companion object for the DefaultResponseReceiver class.
     * Stores and manages the configured responses.
     */
    internal companion object {
        private var defaultResponse: String? = null
        private val partialMatches = mutableMapOf<String, String>()
        private val exactMatches = mutableMapOf<String, String>()
        private val conditionalMatches = mutableMapOf<(String) -> Boolean, String>()

        /**
         * Gets the configured default response.
         *
         * @return The default response, or null if none is configured
         */
        fun getDefaultResponse(): String? {
            return defaultResponse
        }

        /**
         * Gets the configured partial matches.
         *
         * @return A map of patterns to responses
         */
        fun getPartialMatches(): Map<String, String> {
            return partialMatches
        }

        /**
         * Gets the configured exact matches.
         *
         * @return A map of patterns to responses
         */
        fun getExactMatches(): Map<String, String> {
            return exactMatches
        }

        /**
         * Gets the configured conditional matches.
         *
         * @return A map of conditions to responses
         */
        fun getConditionalMatches(): Map<(String) -> Boolean, String> {
            return conditionalMatches
        }

        /**
         * Clears all configured matches and the default response.
         */
        fun clearMatches() {
            partialMatches.clear()
            exactMatches.clear()
            conditionalMatches.clear()
            defaultResponse = null
        }
    }

    /**
     * Sets this response as the default response to be returned when no other response matches.
     *
     * @return The response string for method chaining
     */
    public val asDefaultResponse: String
        get() {
            defaultResponse = response
            return response
        }

    /**
     * Configures the LLM to respond with this string when the user request contains the specified pattern.
     *
     * @param pattern The substring to look for in the user request
     * @return The response string for method chaining
     */
    public infix fun onRequestContains(pattern: String): String {
        partialMatches[pattern] = response
        return response
    }

    /**
     * Configures the LLM to respond with this string when the user request exactly matches the specified pattern.
     *
     * @param pattern The exact string to match in the user request
     * @return The response string for method chaining
     */
    public infix fun onRequestEquals(pattern: String): String {
        exactMatches[pattern] = response
        return response
    }

    /**
     * Configures the LLM to respond with this string when the user request satisfies the specified condition.
     *
     * @param condition A function that evaluates the user request and returns true if it matches
     * @return The response string for method chaining
     */
    public infix fun onCondition(condition: (String) -> Boolean): String {
        conditionalMatches[condition] = response
        return response
    }
}

/**
 * Creates a mock LLM executor for testing.
 *
 * This function provides a convenient way to create a mock LLM executor with the specified
 * tool registry and configuration. It handles the setup of the MockLLMBuilder and applies
 * all the configured responses and tool actions.
 *
 * @param toolRegistry Optional tool registry to be used for tool execution
 * @param init A lambda with receiver that configures the mock LLM executor
 * @return A configured PromptExecutor for testing
 *
 * Example usage:
 * ```kotlin
 * val mockLLMApi = getMockExecutor(toolRegistry) {
 *     // Mock LLM text responses
 *     mockLLMAnswer("Hello!") onRequestContains "Hello"
 *     mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
 *
 *     // Mock LLM tool calls
 *     mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
 *
 *     // Mock tool behavior
 *     mockTool(PositiveToneTool) alwaysReturns "The text has a positive tone."
 *     mockTool(NegativeToneTool) alwaysTells {
 *         println("Negative tone tool called")
 *         "The text has a negative tone."
 *     }
 * }
 * ```
 */
public fun getMockExecutor(
    toolRegistry: ToolRegistry? = null,
    init: MockLLMBuilder.() -> Unit
): PromptExecutor {

    // Clear previous matches
    DefaultResponseReceiver.clearMatches()

    // Call MockLLMBuilder and apply toolRegistry, eventHandler and set currentBuilder to this (to add mocked tool calls)
    val builder = MockLLMBuilder().apply {
        toolRegistry?.let { setToolRegistry(it) }
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
