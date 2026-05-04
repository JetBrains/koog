package ai.koog.agents.testing.tools

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.tokenizer.Tokenizer
import ai.koog.serialization.JSONSerializer
import kotlin.jvm.JvmOverloads
import kotlin.time.Clock

/**
 * Fluent-style builder class for creating a mock prompt executor with configurable tools and behaviors.
 */
@JavaAPI
public class MockExecutorBuilder internal constructor(
    private val serializer: JSONSerializer,
) {
    private var clock: Clock = Clock.System

    private var tokenizer: Tokenizer? = null

    private var handleLastAssistantMessage: Boolean = false

    private var initBlocks: MutableList<MockExecutorDSLBuilder.() -> Unit> = mutableListOf()

    /**
     * Sets the clock instance.
     * @return The updated [MockExecutorBuilder] instance for method chaining.
     */
    public fun clock(clock: Clock): MockExecutorBuilder = apply { this.clock = clock }

    /**
     * Sets the tokenizer.
     * @return The updated [MockExecutorBuilder] instance for method chaining.
     */
    public fun tokenizer(tokenizer: Tokenizer): MockExecutorBuilder = apply { this.tokenizer = tokenizer }

    /**
     * Configures whether the executor should handle the last assistant message during execution.
     * @return The updated [MockExecutorBuilder] instance for method chaining.
     */
    public fun handleLastAssistantMessage(handleLastAssistantMessage: Boolean): MockExecutorBuilder =
        apply { this.handleLastAssistantMessage = handleLastAssistantMessage }

    /**
     * Configures a mock response for a LLM.
     * @return The updated [MockExecutorBuilder] instance for method chaining.
     */
    public fun mockLLMAnswer(text: String): MockLLMAnswerBuilder =
        MockLLMAnswerBuilder(this, text)

    /**
     * Creates a mock for an LLM tool call.
     *
     * @param tool The tool to be called
     * @param args The arguments to pass to the tool
     */
    @JvmOverloads
    public fun <Args> mockLLMToolCall(
        tool: Tool<Args, *>,
        args: Args,
        toolCallId: String? = null
    ): MockLLMToolCallBuilder<Args> = MockLLMToolCallBuilder(this, tool, args, toolCallId)

    /**
     * Creates a mock for a tool.
     *
     * @param tool The tool to be mocked
     */
    public fun <Args, Result> mockTool(
        tool: Tool<Args, Result>
    ): MockToolBuilder<Args, Result> = MockToolBuilder(this, tool)

    /**
     * Builds and returns a configured instance of [ai.koog.prompt.executor.model.PromptExecutor] using the current
     * state of the [MockExecutorBuilder]
     */
    public fun build(): PromptExecutor =
        getMockExecutor(serializer, clock, tokenizer, handleLastAssistantMessage) { initBlocks.forEach { it() } }

    public class MockLLMToolCallBuilder<Args> internal constructor(
        private val mockExecutorBuilder: MockExecutorBuilder,
        private val tool: Tool<Args, *>,
        private val args: Args,
        private val toolCallId: String?
    ) {
        public fun onRequestEquals(pattern: String): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                mockLLMToolCall(tool, args, toolCallId).onRequestEquals(pattern)
            }
        }

        public fun onRequestContains(pattern: String): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                mockLLMToolCall(tool, args, toolCallId).onRequestContains(pattern)
            }
        }

        public fun onCondition(condition: (String) -> Boolean): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                mockLLMToolCall(tool, args, toolCallId).onCondition(condition)
            }
        }
    }

    public class MockToolBuilder<Args, Result> internal constructor(
        private val mockExecutorBuilder: MockExecutorBuilder,
        private val tool: Tool<Args, Result>
    ) {
        public fun alwaysReturns(response: Result): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                mockTool(tool).alwaysReturns(response)
            }
        }

        public fun alwaysTells(action: Action<String>): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                (mockTool(tool) as MockExecutorDSLBuilder.MockToolReceiver<Args, String>).alwaysTells(action::action)
            }
        }

        public fun alwaysDoes(action: Action<Result>): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                mockTool(tool).alwaysDoes(action::action)
            }
        }

        public fun returns(result: Result): MockToolResponseBuilder<Args, Result> =
            MockToolResponseBuilder(mockExecutorBuilder, tool, result)

        public fun interface Action<T> {
            public fun action(): T
        }
    }

    public class MockToolResponseBuilder<Args, Result> internal constructor(
        private val mockExecutorBuilder: MockExecutorBuilder,
        private val tool: Tool<Args, Result>,
        private val result: Result
    ) {
        public fun onArguments(args: Args): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                mockTool(tool).returns(result).onArguments(args)
            }
        }

        public fun onArgumentsMatching(condition: (Args) -> Boolean): MockExecutorBuilder =
            mockExecutorBuilder.apply {
                initBlocks += {
                    mockTool(tool).returns(result).onArgumentsMatching(condition)
                }
            }
    }

    /**
     * A builder class for configuring and managing mocked LLM responses within a [MockExecutorBuilder] context.
     *
     * @param mockExecutorBuilder The parent builder for configuring the mock executor.
     * @param response The mock response to provide based on specified conditions.
     */
    public class MockLLMAnswerBuilder internal constructor(
        private val mockExecutorBuilder: MockExecutorBuilder,
        private val response: String,
    ) {
        /**
         * Sets this response as the default response to be returned when no other response matches.
         * @return The updated [MockLLMAnswerBuilder] instance for method chaining.
         */
        public fun asDefaultResponse(): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                mockLLMAnswer(response).asDefaultResponse
            }
        }

        /**
         * Configures the LLM to respond with this string when the user request contains the specified pattern.
         *
         * @param pattern The substring to look for in the user request
         * @return The updated [MockLLMAnswerBuilder] instance for method chaining.
         */
        public infix fun onRequestContains(pattern: String): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                mockLLMAnswer(response).onRequestContains(pattern)
            }
        }

        /**
         * Configures the LLM to respond with this string when the user request exactly matches the specified pattern.
         *
         * @param pattern The exact string to match in the user request
         * @return The updated [MockLLMAnswerBuilder] instance for method chaining.
         */
        public infix fun onRequestEquals(pattern: String): MockExecutorBuilder = mockExecutorBuilder.apply {
            initBlocks += {
                mockLLMAnswer(response).onRequestEquals(pattern)
            }
        }

        /**
         * Configures the LLM to respond with this string when the user request satisfies the specified condition.
         *
         * @param condition A function that evaluates the user request and returns true if it matches
         * @return The updated [MockLLMAnswerBuilder] instance for method chaining.
         */
        public infix fun onCondition(condition: (String) -> Boolean): MockExecutorBuilder =
            mockExecutorBuilder.apply {
                initBlocks += {
                    mockLLMAnswer(response).onCondition { condition(it) }
                }
            }
    }
}
