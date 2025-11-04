package ai.koog.agents.testing.tools

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.tokenizer.Tokenizer
import kotlinx.datetime.Clock
import java.util.function.Function

/**
 * A builder class for creating a mock prompt executor with configurable tools and behaviors.
 *
 * This builder allows the customization of various components such as the tool registry, clock, tokenizer,
 * and other execution-specific properties. It provides chaining methods to set these components and returns
 * a properly configured mock executor upon calling the `build()` method.
 *
 * The mock executor is primarily designed for testing and simulation scenarios, enabling controlled responses
 * and behavior for Language Learning Models (LLMs) or similar systems.
 */
@JavaAPI
public class MockExecutorBuilder internal constructor() {
    /**
     * Holds an optional reference to a `ToolRegistry` instance.
     *
     * The `toolRegistry` variable is used to maintain the state of a specific `ToolRegistry`,
     * which is responsible for managing a collection of tools for use in the context of the
     * `MockExecutorBuilder` class. This variable may be initialized or updated via the `toolRegistry`
     * method within the builder.
     *
     * This variable is nullable, indicating that the registry may or may not be defined
     * during the lifecycle of a `MockExecutorBuilder` instance.
     */
    private var toolRegistry: ToolRegistry? = null
    /**
     * A configurable clock instance used to control and manipulate time-related operations.
     *
     * The `clock` attribute allows the behavior of time-dependent functionalities to be mocked or adjusted
     * for testing purposes. By default, it uses the [`Clock.System`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.time/-clock/-system.html)
     * implementation, which represents the system clock in the current environment.
     *
     * This property can be set or customized during the building process of `MockExecutorBuilder` to
     * facilitate fine-grained control over time during executions, enabling scenarios such as testing
     * time-sensitive logic or simulating specific timestamps.
     */
    private var clock: Clock = Clock.System
    /**
     * Holds the instance of the `Tokenizer` interface used for tokenizing text and counting tokens.
     * This variable is used within the `MockExecutorBuilder` to configure tokenization behavior for
     * prompts or text processing.
     *
     * The `Tokenizer` allows for estimating the number of tokens in a given string, which is useful
     * for scenarios involving language models (LLMs) where token counts need to be determined client-side.
     *
     * It can be set via the `tokenizer` method in the `MockExecutorBuilder` class.
     * The variable defaults to `null`, and a specific implementation must be provided to function properly.
     */
    private var tokenizer: Tokenizer? = null
    /**
     * Indicates whether the last message from the assistant should be handled during the mock execution process.
     *
     * This flag determines if additional processing or specific actions should be triggered when the assistant's
     * final message is received. It can be toggled to customize behavior during testing scenarios.
     */
    private var handleLastAssistantMessage: Boolean = false

    /**
     * A mutable list of initialization blocks used to configure the `MockLLMBuilder` instances
     * within the `MockExecutorBuilder`. Each initialization block is a lambda that operates on
     * a `MockLLMBuilder` instance to apply custom properties or behaviors during its setup.
     */
    private var initBlocks: MutableList<MockLLMBuilder.() -> Unit> = mutableListOf()

    /**
     * Sets the `toolRegistry` for the `MockExecutorBuilder`.
     *
     * This method allows specifying a `ToolRegistry` instance that will be used during the mock execution process.
     *
     * @param toolRegistry The `ToolRegistry` instance to be set.
     * @return The updated `MockExecutorBuilder` instance for method chaining.
     */
    public fun toolRegistry(toolRegistry: ToolRegistry): MockExecutorBuilder =
        apply { this.toolRegistry = toolRegistry }

    /**
     * Sets the clock instance to be used by the `MockExecutorBuilder`.
     *
     * @param clock The `Clock` instance to configure the builder with.
     * @return The updated `MockExecutorBuilder` instance, to allow for method chaining.
     */
    public fun clock(clock: Clock): MockExecutorBuilder = apply { this.clock = clock }

    /**
     * Sets the tokenizer to be used by the `MockExecutorBuilder`.
     *
     * The tokenizer is responsible for tokenizing text and estimating token counts, which can be
     * used for tasks such as token-based message handling or prompt size estimations.
     *
     * @param tokenizer The tokenizer implementation to be used
     * @return The current instance of `MockExecutorBuilder` for method chaining
     */
    public fun tokenizer(tokenizer: Tokenizer): MockExecutorBuilder = apply { this.tokenizer = tokenizer }

    /**
     * Configures whether the executor should handle the last assistant message during execution.
     *
     * @param handleLastAssistantMessage A boolean value indicating whether the last assistant message
     * should be processed (`true`) or ignored (`false`).
     * @return The current instance of [MockExecutorBuilder] for further configuration.
     */
    public fun handleLastAssistantMessage(handleLastAssistantMessage: Boolean): MockExecutorBuilder =
        apply { this.handleLastAssistantMessage = handleLastAssistantMessage }

    /**
     * Builds and returns a configured instance of `PromptExecutor` using the current
     * state of the `MockExecutorBuilder`. This method applies all initialization
     * blocks defined in the builder and finalizes the setup of the mock executor.
     *
     * @return A `PromptExecutor` instance configured with the current state
     *         of the `MockExecutorBuilder`.
     */
    public fun build(): PromptExecutor =
        getMockExecutor(toolRegistry, clock, tokenizer, handleLastAssistantMessage) { initBlocks.forEach { it() } }

    /**
     * Configures a mock response within the MockExecutorBuilder context for a Language Learning Model (LLM).
     *
     * This function allows defining a mocked LLM response that can be further customized or conditioned using
     * a `MockLLMAnswerBuilder`. The response will be based on the provided text string.
     *
     * @param text The mocked response text to configure for the LLM.
     * @return An instance of MockLLMAnswerBuilder to further configure or set conditions for the mocked response.
     */
    public fun mockLLMAnswer(text: String): MockLLMAnswerBuilder =
        MockLLMAnswerBuilder(this, text)

    /**
     * A builder class for configuring and managing mocked LLM responses within a [MockExecutorBuilder] context.
     *
     * This class allows the specification of mock responses for a Language Learning Model (LLM) based on various patterns
     * or conditions in user input. The responses can be configured to trigger under specific conditions or set as
     * default responses.
     *
     * @constructor Initializes the builder with a [MockExecutorBuilder] and the desired mock response.
     * @param mockExecutorBuilder The parent builder for configuring the mock executor.
     * @param response The mock response to provide based on specified conditions.
     */
    public class MockLLMAnswerBuilder internal constructor(
        private val mockExecutorBuilder: MockExecutorBuilder,
        private val response: String,
    ) {
        /**
         * Sets this response as the default response to be returned when no other response matches.
         *
         * @return The response string for method chaining
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
         * @return The response string for method chaining
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
         * @return The response string for method chaining
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
         * @return The response string for method chaining
         */
        public infix fun onCondition(condition: Function<String, Boolean>): MockExecutorBuilder =
            mockExecutorBuilder.apply {
                initBlocks += {
                    mockLLMAnswer(response).onCondition { condition.apply(it) }
                }
            }
    }
}
