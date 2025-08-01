package ai.koog.prompt.dsl

import ai.koog.prompt.message.Attachment
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
    public fun system(content: String) {
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
    public fun system(init: TextContentBuilder.() -> Unit) {
        system(TextContentBuilder().apply(init).build())
    }

    /**
     * Adds a user message to the prompt with optional attachments.
     *
     * User messages represent input from the user to the language model.
     * This method supports adding text content along with a list of attachments such as images, audio, or documents.
     *
     * @param content The content of the user message.
     * @param attachments The list of attachments associated with the user message. Defaults to an empty list if no attachments are provided.
     */
    public fun user(content: String, attachments: List<Attachment> = emptyList()) {
        messages.add(Message.User(content, RequestMetaInfo.create(clock), attachments))
    }

    /**
     * Adds a user message to the prompt with attachments.
     *
     * User messages represent input from the user to the language model.
     * This method allows attaching content like images, audio, or documents.
     *
     * Example:
     * ```kotlin
     * // Simple text message
     * user("What is the capital of France?")
     *
     * // Message with attachments using a lambda
     * user("Please analyze this image") {
     *     image("photo.jpg")
     * }
     * ```
     *
     * @param content The content of the user message
     * @param block Optional lambda to configure attachments using AttachmentBuilder
     */
    public fun user(content: String, block: AttachmentBuilder.() -> Unit) {
        user(content, AttachmentBuilder().apply(block).build())
    }

    /**
     * Adds a user message to the prompt using a ContentBuilderWithAttachment.
     *
     * This allows for more complex message construction with both text and attachments.
     *
     * Example:
     * ```kotlin
     * user {
     *          text("I have a question about programming.")
     *          text("How do I implement a binary search in Kotlin?")
     *
     *      attachments {
     *          image("screenshot.png")
     *      }
     * }
     * ```
     *
     * @param body The initialization block for the ContentBuilderWithAttachment
     */
    public fun user(body: MessageContentBuilder.() -> Unit) {
        val messageContent = MessageContentBuilder().apply(body).build()
        user(messageContent.content, messageContent.attachments)
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
    public fun assistant(content: String) {
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
    public fun assistant(init: TextContentBuilder.() -> Unit) {
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
    public fun message(message: Message) {
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
    public fun messages(messages: List<Message>) {
        this.messages.addAll(messages)
    }

    /**
     * Builder class for adding tool-related messages to the prompt.
     *
     * This class provides methods for adding tool calls and tool results.
     */
    @PromptDSL
    public inner class ToolMessageBuilder(public val clock: Clock) {
        /**
         * Adds a tool call message to the prompt.
         *
         * Tool calls represent requests to execute a specific tool.
         *
         * @param call The tool call message to add
         */
        public fun call(call: Message.Tool.Call) {
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
        public fun call(id: String?, tool: String, content: String) {
            call(Message.Tool.Call(id, tool, content, ResponseMetaInfo.create(clock)))
        }

        /**
         * Adds a tool result message to the prompt.
         *
         * Tool results represent the output from executing a tool.
         *
         * @param result The tool result message to add
         */
        public fun result(result: Message.Tool.Result) {
            this@PromptBuilder.messages
                .indexOfLast { it is Message.Tool.Call && it.id == result.id }
                .takeIf { it != -1 }
                ?.let { index -> this@PromptBuilder.messages.add(index + 1, result) }
                ?: throw IllegalStateException("Failed to add tool result: no call message with id ${result.id}")
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
        public fun result(id: String?, tool: String, content: String) {
            result(Message.Tool.Result(id, tool, content, RequestMetaInfo.create(clock)))
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
    public fun tool(init: ToolMessageBuilder.() -> Unit) {
        tool.init()
    }

    /**
     * Builds and returns a Prompt object from the current state of the builder.
     *
     * @return A new Prompt object
     */
    internal fun build(): Prompt = Prompt(messages.toList(), id, params)
}
