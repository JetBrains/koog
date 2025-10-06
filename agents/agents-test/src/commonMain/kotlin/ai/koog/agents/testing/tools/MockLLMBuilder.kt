package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.tokenizer.Tokenizer
import kotlinx.datetime.Clock
import kotlin.jvm.JvmName

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
public class ToolCondition<Args, Result>(
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
        tool.name == toolCall.tool && argsCondition(tool.decodeArgs(toolCall.contentJson))

    /**
     * Invokes the tool with the arguments from the tool call.
     *
     * @param toolCall The tool call containing the arguments
     * @return The result produced by the tool
     */
    internal suspend fun invoke(toolCall: Message.Tool.Call) =
        produceResult(tool.decodeArgs(toolCall.contentJson))

    /**
     * Invokes the tool and serializes the result.
     *
     * @param toolCall The tool call containing the arguments
     * @return A pair of the result object and its serialized string representation
     */
    internal suspend fun invokeAndSerialize(toolCall: Message.Tool.Call): Pair<Result, String> {
        val toolResult = produceResult(tool.decodeArgs(toolCall.contentJson))
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
 *
 * @property clock: A clock that is used for mock message timestamps
 * @property tokenizer: Tokenizer that will be used to estimate token counts in mock messages
 */
public class MockLLMBuilder(private val clock: Clock, private val tokenizer: Tokenizer? = null) {
    private val toolCallExactMatches = mutableMapOf<String, List<Message.Tool.Call>>()
    private val toolCallPartialMatches = mutableMapOf<String, List<Message.Tool.Call>>()
    private val toolCallConditionalMatches = mutableMapOf<(String) -> Boolean, List<Message.Tool.Call>>()
    private var toolRegistry: ToolRegistry? = null
    private var toolActions: MutableList<ToolCondition<*, *>> = mutableListOf()

    private val assistantPartialMatches = mutableMapOf<String, List<String>>()
    private val assistantExactMatches = mutableMapOf<String, List<String>>()
    private val conditionalResponses = mutableMapOf<(String) -> Boolean, String>()
    private var defaultResponse: String = ""

    private val moderationPartialMatches = mutableMapOf<String, ModerationResult>()
    private val moderationExactMatches = mutableMapOf<String, ModerationResult>()
    private var defaultModerationResponse: ModerationResult = ModerationResult(
        isHarmful = false,
        categories = emptyMap()
    )

    /**
     * Determines whether the last message handled in a sequence should focus specifically on
     * the most recent message categorized as `Message.Assistant` when resolving mock responses.
     *
     * Useful in scenarios where the mock response handling involves mixed results
     * from the LLM, and there is a need to differentiate between handling the general
     * last message vs the last assistant-specific message.
     */
    public var handleLastAssistantMessage: Boolean = false

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
     * Sets the default moderation response to the provided result.
     *
     * @param result the moderation result to set as the default response
     */
    public fun setDefaultModerationResponse(result: ModerationResult) {
        defaultModerationResponse = result
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
     * @param pattern The exact input string to match
     * @param tool The tool to be called when the input matches
     * @param args The arguments to pass to the tool
     */
    public fun <Args> addLLMAnswerExactPattern(
        pattern: String,
        tool: Tool<Args, *>,
        args: Args,
        toolCallId: String?
    ) {
        toolCallExactMatches[pattern] = tool.encodeArgsToString(args).let { toolContent ->
            listOf(
                Message.Tool.Call(
                    id = toolCallId,
                    tool = tool.name,
                    content = toolContent,
                    metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenizer?.countTokens(toolContent))
                )
            )
        }
    }

    /**
     * Adds a partial pattern match for an LLM answer that triggers a tool call.
     *
     * @param pattern The exact input string to match
     * @param tool The tool to be called when the input matches
     * @param args The arguments to pass to the tool
     */
    public fun <Args> addLLMAnswerPartialPattern(
        pattern: String,
        tool: Tool<Args, *>,
        args: Args
    ) {
        toolCallPartialMatches[pattern] = tool.encodeArgsToString(args).let { toolContent ->
            listOf(
                Message.Tool.Call(
                    id = null,
                    tool = tool.name,
                    content = toolContent,
                    metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenizer?.countTokens(toolContent))
                )
            )
        }
    }

    /**
     * Adds a partial pattern match for an LLM answer that triggers a set of tool calls.
     *
     * @param pattern The substring pattern to partially match in the user request.
     * @param toolCalls A list of pairs, where each pair consists of a tool and the arguments
     *                  to pass to the tool. These tool calls will be triggered when the input matches the pattern.
     */
    public fun <Args> addLLMAnswerPartialPattern(
        pattern: String,
        toolCalls: List<Pair<Tool<Args, *>, Args>>
    ) {
        toolCallPartialMatches[pattern] = toolCalls.map { (tool, args) ->
            tool.encodeArgsToString(args).let { toolContent ->
                Message.Tool.Call(
                    id = null,
                    tool = tool.name,
                    content = toolContent,
                    metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenizer?.countTokens(toolContent))
                )
            }
        }
    }

    /**
     * Adds an exact pattern match for an LLM answer that triggers a set of tool calls.
     *
     * @param pattern The exact input string to match
     * @param toolCalls Tool calls with args
     */
    public fun <Args> addLLMAnswerExactPattern(
        pattern: String,
        toolCalls: List<Pair<Tool<Args, *>, Args>>
    ) {
        toolCallExactMatches[pattern] = toolCalls.map { (tool, args) ->
            tool.encodeArgsToString(args).let { toolContent ->
                Message.Tool.Call(
                    id = null,
                    tool = tool.name,
                    content = toolContent,
                    metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenizer?.countTokens(toolContent))
                )
            }
        }
    }

    /**
     * Adds an exact pattern match for an LLM answer that triggers a set of tool calls
     * with predefined responses.
     *
     * @param pattern The exact input string to match.
     * @param toolCalls A list of tool call and argument pairs to be triggered when the input matches.
     * @param responses A list of response strings corresponding to each tool call.
     */
    public fun <Args> addLLMAnswerExactPattern(
        pattern: String,
        toolCalls: List<Pair<Tool<Args, *>, Args>>,
        responses: List<String>
    ) {
        toolCallExactMatches[pattern] = toolCalls.map { (tool, args) ->
            tool.encodeArgsToString(args).let { toolContent ->
                Message.Tool.Call(
                    id = null,
                    tool = tool.name,
                    content = toolContent,
                    metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenizer?.countTokens(toolContent))
                )
            }
        }

        assistantExactMatches[pattern] = responses
    }

    /**
     * Adds a conditional match for a tool call to the LLM answer processing system.
     * This method associates a condition with a tool and its arguments, allowing conditional execution
     * of the tool when the specified condition matches.
     *
     * @param condition A predicate function that takes a string input and returns a Boolean, indicating whether the condition is met.
     * @param tool The tool object to be called if the condition is satisfied.
     * @param args The arguments to be passed to the tool, which will be encoded to a string for the tool call.
     */
    public fun <Args> addLLMAnswerConditionalMatches(
        condition: (String) -> Boolean,
        tool: Tool<Args, *>,
        args: Args
    ) {
        toolCallConditionalMatches[condition] = tool.encodeArgsToString(args).let { toolContent ->
            listOf(
                Message.Tool.Call(
                    id = null,
                    tool = tool.name,
                    content = toolContent,
                    metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenizer?.countTokens(toolContent))
                )
            )
        }
    }

    /**
     * Adds a specific moderation response for an exact pattern match.
     *
     * @param pattern The exact string pattern that should be matched.
     * @param response*/
    public fun addModerationResponseExactPattern(pattern: String, response: ModerationResult) {
        moderationExactMatches[pattern] = response
    }

    /**
     * Adds a partial pattern match for an LLM answer that triggers a set of tool calls
     * with predefined responses.
     *
     * @param pattern The substring pattern to partially match in the user request.
     * @param toolCalls A list of tool call and argument pairs to be triggered when the input matches.
     * @param responses A list of response strings corresponding to each tool call.
     */
    public fun <Args> addLLMAnswerPartialPattern(
        pattern: String,
        toolCalls: List<Pair<Tool<Args, *>, Args>>,
        responses: List<String>
    ) {
        toolCallPartialMatches[pattern] = toolCalls.map { (tool, args) ->
            tool.encodeArgsToString(args).let { toolContent ->
                Message.Tool.Call(
                    id = null,
                    tool = tool.name,
                    content = toolContent,
                    metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenizer?.countTokens(toolContent))
                )
            }
        }

        assistantPartialMatches[pattern] = responses
    }

    /**
     * Associates a given moderation response with a specific partial pattern.
     *
     * @param pattern The string pattern to be used as a key for the moderation response.
     * @param response The ModerationResult object that corresponds to the given pattern.
     */
    public fun addModerationResponsePartialPattern(pattern: String, response: ModerationResult) {
        moderationPartialMatches[pattern] = response
    }

    /**
     * Adds a tool action to be executed when a tool call matches the specified condition.
     *
     * @param tool The tool to be mocked
     * @param argsCondition A function that determines if the tool call arguments match this action
     * @param action A function that produces the result when the condition is satisfied
     */
    public fun <Args, Result> addToolAction(
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
    public fun <Args> mockLLMToolCall(
        tool: Tool<Args, *>,
        args: Args,
        toolCallId: String? = null
    ): ToolCallReceiver<Args> =
        ToolCallReceiver(tool, args, toolCallId, this)

    /**
     * Creates a mock for a list of LLM tool calls.
     *
     * This method is used to define how the LLM should respond with multiple tool calls
     * when specific inputs or conditions are encountered during testing.
     *
     * @param toolCalls A list of pairs, where each pair consists of a tool and corresponding arguments.
     *                  These define the mock calls to be returned by the LLM.
     * @return A [MultiToolCallReceiver] to configure further mock behavior for the provided tool calls.
     */
    public fun <Args> mockLLMToolCall(
        toolCalls: List<Pair<Tool<Args, *>, Args>>
    ): MultiToolCallReceiver<Args> =
        MultiToolCallReceiver(toolCalls, this)

    /**
     * Creates a mock response with a combination of tool calls and predefined string responses.
     *
     * This method is used to define a mixed behavior where the LLM produces a sequence of tool
     * calls along with corresponding responses for testing purposes.
     *
     * @param toolCalls A list of pairs, where each pair consists of a tool and the corresponding arguments.
     * @param responses A list of response strings corresponding to the provided tool calls. These define
     *                  what the LLM should output for each tool call.
     * @return A [MixedResultsReceiver] to configure further mock behavior for the provided tool calls and responses.
     */
    public fun <Args> mockLLMMixedResponse(
        toolCalls: List<Pair<Tool<Args, *>, Args>>,
        responses: List<String>
    ): MixedResultsReceiver<Args> =
        MixedResultsReceiver(toolCalls, responses, this)

    /**
     * Creates a mock for a tool.
     *
     * This method is used to define how a tool should behave when it is called
     * during testing.
     *
     * @param tool The tool to be mocked
     * @return A [MockToolReceiver] for further configuration
     */
    public fun <Args, Result> mockTool(
        tool: Tool<Args, Result>
    ): MockToolReceiver<Args, Result> {
        return MockToolReceiver(tool, this)
    }

    /**
     * Configures the LLM to respond with this string when the user request contains the specified pattern.
     *
     * @param pattern The substring to look for in the user request
     * @return The MockLLMBuilder instance for method chaining
     */
    public infix fun String.onUserRequestContains(pattern: String): MockLLMBuilder {
        assistantPartialMatches[pattern] = listOf(this)
        return this@MockLLMBuilder
    }

    /**
     * Configures the LLM to respond with this string when the user request exactly matches the specified pattern.
     *
     * @param pattern The exact string to match in the user request
     * @return The MockLLMBuilder instance for method chaining
     */
    public infix fun String.onUserRequestEquals(pattern: String): MockLLMBuilder {
        assistantExactMatches[pattern] = listOf(this)
        return this@MockLLMBuilder
    }

    /**
     * Configures the LLM to respond with this string when the user request satisfies the specified condition.
     *
     * @param condition A function that evaluates the user request and returns true if it matches
     * @return The MockLLMBuilder instance for method chaining
     */
    public infix fun String.onCondition(condition: (String) -> Boolean): MockLLMBuilder {
        conditionalResponses[condition] = this
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
    public class ToolCallReceiver<Args>(
        private val tool: Tool<Args, *>,
        private val args: Args,
        private val toolCallId: String?,
        private val builder: MockLLMBuilder
    ) {
        /**
         * Configures the LLM to respond with a tool call when the user request exactly matches the specified pattern.
         *
         * @param pattern The exact string to match in the user request
         * @return The [pattern] string for method chaining
         */
        public infix fun onRequestEquals(pattern: String): String {
            // Using the llmAnswer directly as the response, which should contain the tool call JSON
            builder.addLLMAnswerExactPattern(pattern, tool = tool, args = args, toolCallId = toolCallId)

            // Return the llmAnswer as is, which should be a valid tool call JSON
            return pattern
        }

        /**
         * Configures the system to partially match user requests containing the specified pattern.
         * If the pattern is found within a user request, the associated tool call response will be triggered.
         *
         * @param pattern The substring pattern to match within user requests.
         * @return The [pattern] string for method chaining
         */
        public infix fun onRequestContains(pattern: String): String {
            builder.addLLMAnswerPartialPattern(pattern, tool, args)

            return pattern
        }

        /**
         * Configures the LLM to respond with a tool call based on a custom condition.
         *
         * @param condition A predicate function that takes a string input and returns a Boolean.
         * The condition determines whether the associated tool call should be triggered.
         */
        public infix fun onCondition(condition: (String) -> Boolean) {
            builder.addLLMAnswerConditionalMatches(condition, tool, args)
        }
    }

    /**
     * Represents a class responsible for handling and managing mixed tool call results
     * based on mock responses and predefined configurations.
     *
     * @param Args The type of tool arguments.
     * @property toolCalls A list of tool-arguments pairs representing mocked tool calls and their configurations.
     * @property responses A list of response strings to be used when handling tool call results.
     * @property builder An instance of [MockLLMBuilder] used to configure and mock behaviors.
     */
    public class MixedResultsReceiver<Args>(
        private val toolCalls: List<Pair<Tool<Args, *>, Args>>,
        private val responses: List<String>,
        private val builder: MockLLMBuilder
    ) {
        /**
         * Configures the LLM to respond with a tool call when the user request exactly matches the specified pattern.
         *
         * @param pattern The exact string to match in the user request
         * @return The [pattern] string for method chaining
         */
        public infix fun onRequestEquals(pattern: String): String {
            // Using the llmAnswer directly as the response, which should contain the tool call JSON
            builder.addLLMAnswerExactPattern(pattern, toolCalls, responses)

            // Return the llmAnswer as is, which should be a valid tool call JSON
            return pattern
        }

        /**
         * Configures the system to partially match user requests containing the specified pattern.
         * If the pattern is found within a user request, the associated tool call response will be triggered.
         *
         * @param pattern The substring pattern to match within user requests.
         * @return The [pattern] string for method chaining
         */
        public infix fun onRequestContains(pattern: String): String {
            builder.addLLMAnswerPartialPattern(pattern, toolCalls, responses)

            return pattern
        }
    }

    /**
     * Receiver class for configuring tool call responses from the LLM.
     * This class is part of the fluent API for configuring how the LLM should respond
     * with tool calls when it receives specific inputs.
     */
    public class MultiToolCallReceiver<Args>(
        private val toolCalls: List<Pair<Tool<Args, *>, Args>>,
        private val builder: MockLLMBuilder
    ) {
        /**
         * Configures the LLM to respond with a tool call when the user request exactly matches the specified pattern.
         *
         * @param pattern The exact string to match in the user request
         * @return The [pattern] string for method chaining
         */
        public infix fun onRequestEquals(pattern: String): String {
            // Using the llmAnswer directly as the response, which should contain the tool call JSON
            builder.addLLMAnswerExactPattern(pattern, toolCalls)

            // Return the llmAnswer as is, which should be a valid tool call JSON
            return pattern
        }

        /**
         * Configures the system to partially match user requests containing the specified pattern.
         * If the pattern is found within a user request, the associated tool call response will be triggered.
         *
         * @param pattern The substring pattern to match within user requests.
         * @return The [pattern] string for method chaining
         */
        public infix fun onRequestContains(pattern: String): String {
            builder.addLLMAnswerPartialPattern(pattern, toolCalls)

            return pattern
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
    public class MockToolReceiver<Args, Result>(
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
        public class MockToolResponseBuilder<Args, Result>(
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
    public infix fun <Args> MockToolReceiver<Args, ToolResult.Text>.alwaysReturns(response: String): Unit =
        alwaysReturns(ToolResult.Text(response))

    /**
     * Convenience extension function for configuring a text tool to always execute the specified action
     * and return its string result.
     *
     * @param action A function that produces the string result
     * @return The result of the alwaysDoes call
     */
    public infix fun <Args> MockToolReceiver<Args, String>.alwaysTells(
        action: suspend () -> String
    ): Unit =
        alwaysDoes { action() }

    /**
     * Convenience extension function for configuring a text tool to always execute the specified action
     * and return its string result.
     *
     * @param action A function that produces the string result
     * @return The result of the alwaysDoes call
     */
    @JvmName("alwaysTellsText")
    public infix fun <Args> MockToolReceiver<Args, ToolResult.Text>.alwaysTells(
        action: suspend () -> String
    ): Unit =
        alwaysDoes { ToolResult.Text(action()) }

    /**
     * Convenience extension function for configuring a text tool to execute the specified action
     * and return its string result when it receives matching arguments.
     *
     * @param action A function that produces the string result
     * @return The result of the does call
     */
    public infix fun <Args> MockToolReceiver<Args, ToolResult.Text>.doesStr(
        action: suspend () -> String
    ): MockToolReceiver.MockToolResponseBuilder<Args, ToolResult.Text> =
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
        // Exact Matches
        val processedAssistantExactMatches = assistantExactMatches.mapValues { (_, value) ->
            val texts = value.map { text -> text.trimIndent() }
            texts.map { text ->
                Message.Assistant(
                    text,
                    ResponseMetaInfo.create(clock, outputTokensCount = tokenizer?.countTokens(text))
                )
            }
        }

        val combinedExactMatches = (processedAssistantExactMatches.keys + toolCallExactMatches.keys).associateWith { key ->
            val assistantList = processedAssistantExactMatches[key] ?: emptyList()
            val toolCallList = toolCallExactMatches[key] ?: emptyList()
            assistantList + toolCallList
        }

        // Partial Matches
        val processedAssistantPartialMatches = assistantPartialMatches.mapValues { (_, value) ->
            val texts = value.map { text -> text.trimIndent() }
            texts.map { text ->
                Message.Assistant(
                    text,
                    ResponseMetaInfo.create(clock, outputTokensCount = tokenizer?.countTokens(text))
                )
            }
        }

        val combinedPartialMatches =
            (processedAssistantPartialMatches.keys + toolCallPartialMatches.keys).associateWith { key ->
                val assistantList = processedAssistantPartialMatches[key] ?: emptyList()
                val toolCallList = toolCallPartialMatches[key] ?: emptyList()
                assistantList + toolCallList
            }

        // Conditional Matches
        val processedAssistantConditionalMatches: Map<(String) -> Boolean, List<Message.Response>> =
            conditionalResponses.takeIf { it.isNotEmpty() }?.mapValues { (_, textResponse) ->
                listOf(
                    Message.Assistant(
                        content = textResponse,
                        metaInfo = ResponseMetaInfo.create(clock)
                    )
                )
            } ?: emptyMap()

        val combinedConditionalMatches = (processedAssistantConditionalMatches.keys + toolCallConditionalMatches.keys).associateWith { key ->
            val assistantList = processedAssistantConditionalMatches[key] ?: emptyList()
            val toolCallList = toolCallConditionalMatches[key] ?: emptyList()
            assistantList + toolCallList
        }

        val responseMatcher = ResponseMatcher(
            partialMatches = combinedPartialMatches.takeIf { it.isNotEmpty() },
            exactMatches = combinedExactMatches.takeIf { it.isNotEmpty() },
            conditional = combinedConditionalMatches,
            defaultResponse = listOf(Message.Assistant(defaultResponse, ResponseMetaInfo.create(clock)))
        )

        val moderationResponseMatcher = ResponseMatcher(
            partialMatches = moderationPartialMatches,
            exactMatches = moderationExactMatches,
            conditional = null, // TODO: support later once required
            defaultResponse = defaultModerationResponse
        )

        return MockLLMExecutor(
            handleLastAssistantMessage,
            responseMatcher = responseMatcher,
            moderationResponseMatcher = moderationResponseMatcher,
            toolRegistry = toolRegistry,
            toolActions = toolActions,
            clock = clock,
            tokenizer = tokenizer
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
 * @param clock: A clock that is used for mock message timestamps
 * @param tokenizer: Tokenizer that will be used to estimate token counts in mock messages
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
    clock: Clock = Clock.System,
    tokenizer: Tokenizer? = null,
    handleLastAssistantMessage: Boolean = false,
    init: MockLLMBuilder.() -> Unit
): PromptExecutor {
    // Clear previous matches
    DefaultResponseReceiver.clearMatches()

    // Call MockLLMBuilder and apply toolRegistry, eventHandler and set currentBuilder to this (to add mocked tool calls)
    val builder = MockLLMBuilder(clock, tokenizer).apply {
        this.handleLastAssistantMessage = handleLastAssistantMessage
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
