package ai.jetbrains.code.prompt.dsl

import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.params.LLMParams
import ai.jetbrains.code.prompt.text.TextContentBuilder

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
 */
@PromptDSL
public class PromptBuilder internal constructor(private val id: String, private val params: LLMParams = LLMParams()) {
    private val messages = mutableListOf<Message>()

    internal companion object {
        internal fun from(prompt: Prompt): PromptBuilder = PromptBuilder(
            prompt.id,
            prompt.params
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
        messages.add(Message.System(content))
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
     * Adds a user message to the prompt.
     *
     * User messages represent input from the user to the language model.
     *
     * Example:
     * ```kotlin
     * user("What is the capital of France?")
     * ```
     *
     * @param content The content of the user message
     */
    public fun user(content: String) {
        messages.add(Message.User(content))
    }

    /**
     * Adds a user message to the prompt using a TextContentBuilder.
     *
     * This allows for more complex message construction.
     *
     * Example:
     * ```kotlin
     * user {
     *     text("I have a question about programming.")
     *     text("How do I implement a binary search in Kotlin?")
     * }
     * ```
     *
     * @param init The initialization block for the TextContentBuilder
     */
    public fun user(init: TextContentBuilder.() -> Unit) {
        user(TextContentBuilder().apply(init).build())
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
        messages.add(Message.Assistant(content))
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
     * message(Message.System("You are a helpful assistant."))
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
     *     Message.System("You are a helpful assistant."),
     *     Message.User("What is the capital of France?")
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
    public inner class ToolMessageBuilder() {
        /**
         * Adds a tool call message to the prompt.
         *
         * Tool calls represent requests to execute a specific tool.
         *
         * @param call The tool call message to add
         */
        public fun call(call: Message.Tool.Call) {
            messages.add(call)
        }

        /**
         * Adds a tool result message to the prompt.
         *
         * Tool results represent the output from executing a tool.
         *
         * @param result The tool result message to add
         */
        public fun result(result: Message.Tool.Result) {
            messages
                .indexOfLast { it is Message.Tool.Call && it.id == result.id }
                .takeIf { it != -1 }
                ?.let { index -> messages.add(index + 1, result) }
                ?: throw IllegalStateException("Failed to add tool result: no call message with id ${result.id}")
        }
    }

    private val tool = ToolMessageBuilder()

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
