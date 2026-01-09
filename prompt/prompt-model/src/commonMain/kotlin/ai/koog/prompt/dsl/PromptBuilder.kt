package ai.koog.prompt.dsl

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.text.TextContentBuilder
import kotlinx.datetime.Clock

/**
 * A builder class for creating prompts using a DSL approach.
 *
 * PromptBuilder allows constructing prompts by adding different types of messages
 * (system, user, assistant, tool) in a structured way.
 *
 * Example usage:
 * ```kotlin
 * val prompt = prompt("example-prompt") {
 *     system("You are a helpful assistant.")
 *     user("What is the capital of France?")
 * }
 * ```
 *
 * @property id The identifier for the prompt
 * @property params The parameters for the language model
 * @property clock The clock used for timestamps of messages
 */
@PromptDSL
@JavaAPI
public class PromptBuilder internal constructor(
    private val id: String,
    private val params: LLMParams = LLMParams(),
    private val clock: Clock = Clock.System
) {
    private val messages = mutableListOf<Message>()

    internal companion object {
        internal fun from(prompt: Prompt, clock: Clock = Clock.System): PromptBuilder = PromptBuilder(
            prompt.id,
            prompt.params,
            clock
        ).apply {
            messages.addAll(prompt.messages)
        }
    }

    /**
     * Adds a system message to the prompt.
     *
     * System messages provide instructions or context to the language model.
     *
     * Example:
     * ```kotlin
     * system("You are a helpful assistant.")
     * ```
     *
     * @param content The content of the system message
     */
    @JavaAPI
    public fun system(content: String): PromptBuilder = apply {
        messages.add(Message.System(content, RequestMetaInfo.create(clock)))
    }

    /**
     * Adds a system message to the prompt using a TextContentBuilder.
     *
     * This allows for more complex message construction.
     *
     * Example:
     * ```kotlin
     * system {
     *     text("You are a helpful assistant.")
     *     text("Always provide accurate information.")
     * }
     * ```
     *
     * @param init The initialization block for the TextContentBuilder
     */
    @JavaAPI
    public fun system(init: TextContentBuilder.() -> Unit): PromptBuilder = apply {
        system(TextContentBuilder().apply(init).build())
    }

    /**
     * Adds a user message to the prompt with optional attachments.
     *
     * User messages represent input from the user to the language model.
     * This method supports adding parts of the message such as text content or attachments.
     *
     * @param parts Parts of the user message
     */
    @JavaAPI
    public fun user(parts: List<ContentPart>): PromptBuilder = apply {
        messages.add(Message.User(parts, RequestMetaInfo.create(clock)))
    }

    /**
     * Adds a user message to the prompt.
     *
     * User messages represent input from the user to the language model.
     * This method supports adding text content.
     *
     * @param content Content of the user message
     */
    @JavaAPI
    public fun user(content: String): PromptBuilder = apply {
        messages.add(Message.User(content, RequestMetaInfo.create(clock)))
    }

    /**
     * Adds a user message to the prompt with optional attachments.
     *
     * User messages represent input from the user to the language model.
     * This method supports adding text content.
     *
     * @param content Content of the user message
     * @param block Lambda to configure attachments using [ContentPartsBuilder]
     */
    @JavaAPI
    @Deprecated("Use user(block: ContentPartsBuilder.() -> Unit instead.")
    public fun user(content: String, block: ContentPartsBuilder.() -> Unit): PromptBuilder = apply {
        user(content, ContentPartsBuilder().apply(block).build())
    }

    /**
     * Adds a user message to the prompt with optional attachments.
     *
     * User messages represent input from the user to the language model.
     * This method supports adding text content.
     *
     * @param content Content of the user message
     * @param attachments Attachments to be added to the message
     */
    @JavaAPI
    @Deprecated("Use user(block: ContentPartsBuilder.() -> Unit instead.")
    public fun user(content: String, attachments: List<ContentPart> = emptyList()): PromptBuilder = apply {
        user(listOf(ContentPart.Text(content)) + attachments)
    }

    /**
     * Adds a user message to the prompt with attachments.
     *
     * User messages represent input from the user to the language model.
     * This method allows adding parts of the message such as text content or attachments using a [ContentPartsBuilder].
     *
     * Example:```
     * user {
     *     test("Image 1:")
     *     image("photo1.jpg")
     *     test("Image 2:")
     *     image("photo3.jpg")
     * }
     * ```
     *
     * @param block Lambda to configure attachments using [ContentPartsBuilder]
     */
    @JavaAPI
    public fun user(block: ContentPartsBuilder.() -> Unit): PromptBuilder = apply {
        user(ContentPartsBuilder().apply(block).build())
    }

    /**
     * Adds an assistant message to the prompt.
     *
     * Assistant messages represent responses from the language model.
     *
     * Example:
     * ```kotlin
     * assistant("The capital of France is Paris.")
     * ```
     *
     * @param content The content of the assistant message
     */
    @JavaAPI
    public fun assistant(content: String): PromptBuilder = apply {
        messages.add(Message.Assistant(content, finishReason = null, metaInfo = ResponseMetaInfo.create(clock)))
    }

    /**
     * Adds an assistant message to the prompt using a TextContentBuilder.
     *
     * This allows for more complex message construction.
     *
     * Example:
     * ```kotlin
     * assistant {
     *     text("The capital of France is Paris.")
     *     text("It's known for landmarks like the Eiffel Tower.")
     * }
     * ```
     *
     * @param init The initialization block for the TextContentBuilder
     */
    @JavaAPI
    public fun assistant(init: TextContentBuilder.() -> Unit): PromptBuilder = apply {
        assistant(TextContentBuilder().apply(init).build())
    }

    /**
     * Adds a generic message to the prompt.
     *
     * This method allows adding any type of Message object.
     *
     * Example:
     * ```kotlin
     * message(Message.System("You are a helpful assistant.", metaInfo = ...))
     * ```
     *
     * @param message The message to add
     */
    @JavaAPI
    public fun message(message: Message): PromptBuilder = apply {
        messages.add(message)
    }

    /**
     * Adds multiple messages to the prompt.
     *
     * This method allows adding a list of Message objects.
     *
     * Example:
     * ```kotlin
     * messages(listOf(
     *     Message.System("You are a helpful assistant.", metaInfo = ...),
     *     Message.User("What is the capital of France?", metaInfo = ...)
     * ))
     * ```
     *
     * @param messages The list of messages to add
     */
    @JavaAPI
    public fun messages(messages: List<Message>): PromptBuilder = apply {
        this.messages.addAll(messages)
    }

    /**
     * Builder class for adding tool-related messages to the prompt.
     *
     * This class provides methods for adding tool calls and tool results.
     */
    @JavaAPI
    @PromptDSL
    public inner class ToolMessageBuilder(public val clock: Clock) {
        /**
         * Adds a tool call message to the prompt.
         *
         * Tool calls represent requests to execute a specific tool.
         *
         * @param call The tool call message to add
         */
        @JavaAPI
        public fun call(call: Message.Tool.Call): ToolMessageBuilder = apply {
            this@PromptBuilder.messages.add(call)
        }

        /**
         * Adds a tool call message to the prompt.
         *
         * This method creates a `Message.Tool.Call` instance and adds it to the message list.
         * The tool call represents a request to execute a specific tool with the provided parameters.
         *
         * @param id The unique identifier for the tool call message.
         * @param tool The name of the tool being called.
         * @param content The content or payload of the tool call.
         */
        @JavaAPI
        public fun call(id: String?, tool: String, content: String): ToolMessageBuilder = apply {
            call(Message.Tool.Call(id, tool, content, ResponseMetaInfo.create(clock)))
        }

        /**
         * Adds a tool result message to the prompt.
         *
         * Tool results represent the output from executing a tool.
         *
         * This method ensures that the corresponding tool call message exists in the prompt
         * before adding the result. If the tool call is missing, it will be synthesized and
         * added to maintain proper conversation flow.
         *
         * Problematic cases could potentially occur, when:
         * 1. LLM providers concatenate tool names/args and normalize/split them, producing
         *    synthesized calls that were not part of the original prompt history
         * 2. Tool calls with null IDs get processed separately
         * 3. Parallel tool execution results arrive before calls are recorded in prompt
         *
         * @param result The tool result message to add
         */
        @JavaAPI
        public fun result(result: Message.Tool.Result): ToolMessageBuilder = apply {
            val existingCallIndex = this@PromptBuilder.messages
                .indexOfLast { it is Message.Tool.Call && it.id == result.id }

            if (existingCallIndex != -1) {
                // Normal case: a corresponding tool call exists, so we just add its result after it
                this@PromptBuilder.messages.add(existingCallIndex + 1, result)
            } else {
                // Missing tool call case: synthesize the call message and ensure all originating tool-call messages exist in the prompt before adding results
                if (result.id != null) {
                    val synthesizedCall = Message.Tool.Call(
                        id = result.id,
                        tool = result.tool,
                        content = "Synthesized call for result",
                        metaInfo = ResponseMetaInfo.create(clock)
                    )
                    this@PromptBuilder.messages.add(synthesizedCall)
                }
                // Add the result message at the end after a synthetic tool call
                this@PromptBuilder.messages.add(result)
            }
        }

        /**
         * Adds a tool result message to the prompt.
         *
         * This method creates a `Message.Tool.Result` instance and adds it to the message list.
         * Tool results represent the output from executing a tool with the provided parameters.
         *
         * @param id The unique identifier for the tool result message.
         * @param tool The name of the tool that provided the result.
         * @param content The content or payload of the tool result.
         */
        @JavaAPI
        public fun result(id: String?, tool: String, content: String): ToolMessageBuilder = apply {
            result(Message.Tool.Result(id, tool, content, RequestMetaInfo.create(clock)))
        }
    }

    /**
     * A builder class for constructing tool result messages and appending them to a `PromptBuilder`.
     *
     * @param clock Provides the current time, allowing for temporal operations.
     * @param call A lambda for configuring the `ToolMessageBuilder` instance.
     * @param promptBuilder The parent builder to which tool result messages are added.
     */
    @JavaAPI
    public class ToolResultMessageBuilder(
        private val clock: Clock,
        private val call: ToolMessageBuilder.() -> Unit,
        private val promptBuilder: PromptBuilder
    ) {
        /**
         * Adds a tool result to the prompt builder by invoking the provided tool call
         * and applying the given tool result.
         *
         * @param result the tool result to be added to the prompt builder
         * @return the updated prompt builder after applying the tool result
         */
        @JavaAPI
        public fun toolResult(result: Message.Tool.Result): PromptBuilder = promptBuilder.apply {
            tool {
                call()
                result(result)
            }
        }

        /**
         * Appends a tool result to the promptBuilder.
         *
         * @param id The identifier of the tool result, or null if there is no specific identifier.
         * @param tool The name of the tool associated with the result.
         * @param content The content or output of the tool.
         * @return The updated PromptBuilder instance.
         */
        @JavaAPI
        public fun toolResult(id: String?, tool: String, content: String): PromptBuilder = promptBuilder.apply {
            tool {
                call()
                result(id, tool, content)
            }
        }
    }

    private val tool = ToolMessageBuilder(clock)

    /**
     * Adds tool-related messages to the prompt using a ToolMessageBuilder.
     *
     * Example:
     * ```kotlin
     * tool {
     *     call(Message.Tool.Call("calculator", "{ \"operation\": \"add\", \"a\": 5, \"b\": 3 }"))
     *     result(Message.Tool.Result("calculator", "8"))
     * }
     * ```
     *
     * @param init The initialization block for the ToolMessageBuilder
     */
    @JavaAPI
    public fun tool(init: ToolMessageBuilder.() -> Unit): PromptBuilder = apply {
        tool.init()
    }

    /**
     * Creates a ToolResultMessageBuilder initialized with the provided tool call.
     *
     * @param call The tool call message to be included in the builder.
     * @return A new instance of ToolResultMessageBuilder configured with the specified tool call.
     */
    @JavaAPI
    public fun toolCall(call: Message.Tool.Call): ToolResultMessageBuilder =
        ToolResultMessageBuilder(clock, { call(call) }, this)

    /**
     * Adds a tool call message and initializes a `ToolResultMessageBuilder`.
     *
     * This method creates a tool call message using the provided parameters and
     * sets up the builder for further configuration of tool result messages.
     *
     * @param id A unique identifier for the tool call message. It can be null.
     * @param tool The name of the tool to be invoked.
     * @param content The content or payload associated with the tool call.
     * @return A `ToolResultMessageBuilder` for adding tool result messages.
     */
    @JavaAPI
    public fun toolCall(id: String?, tool: String, content: String): ToolResultMessageBuilder =
        ToolResultMessageBuilder(clock, { call(id, tool, content) }, this)

    /**
     * Builds and returns a Prompt object from the current state of the builder.
     *
     * @return A new Prompt object
     */
    @JavaAPI
    public fun build(): Prompt = Prompt(messages.toList(), id, params)
}
