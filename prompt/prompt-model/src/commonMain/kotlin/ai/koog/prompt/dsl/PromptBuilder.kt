package ai.koog.prompt.dsl

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.CacheControl
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.text.TextContentBuilder
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmOverloads

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
    private val clock: KoogClock = KoogClock.System
) {
    private val messages = mutableListOf<Message>()

    internal companion object {
        internal fun from(prompt: Prompt, clock: KoogClock = KoogClock.System): PromptBuilder = PromptBuilder(
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
     * @param cache Optional cache control to apply after this tool definition.
     */
    @JavaAPI
    @JvmOverloads
    public fun system(content: String, cache: CacheControl? = null): PromptBuilder = apply {
        messages.add(
            Message.System(
                content = content,
                metaInfo = RequestMetaInfo.create(clock),
                cache = cache
            )
        )
    }

    /**
     * Adds a system message to the prompt using a TextContentBuilder.
     *
     * @param cache Optional cache control to apply after this tool definition.
     * @param init The initialization block for the TextContentBuilder
     */
    @JavaAPI
    public fun system(cache: CacheControl? = null, init: TextContentBuilder.() -> Unit): PromptBuilder = apply {
        system(TextContentBuilder().apply(init).build(), cache)
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
    public fun system(init: TextContentBuilder.() -> Unit): PromptBuilder = system(null, init)

    /**
     * Adds a user message to the prompt with optional attachments.
     *
     * User messages represent input from the user to the language model.
     * This method supports adding parts of the message such as text content or attachments.
     *
     * @param parts Parts of the user message
     */
    @JavaAPI
    @JvmOverloads
    public fun user(parts: List<MessagePart.RequestPart>): PromptBuilder = apply {
        messages.add(Message.User(id = null, parts = parts, metaInfo = RequestMetaInfo.create(clock)))
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
    @JvmOverloads
    public fun user(content: String, cache: CacheControl? = null): PromptBuilder = apply {
        messages.add(
            Message.User(
                content = content,
                metaInfo = RequestMetaInfo.create(clock),
                cache = cache
            )
        )
    }

    /**
     * Adds a user message to the prompt with attachments.
     *
     * User messages represent input from the user to the language model.
     * This method allows adding parts of the message such as text content or attachments using a [RequestMessagePartsBuilder].
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
     * @param block Lambda to configure attachments using [ContentMessagePartsBuilder]
     */
    @JavaAPI
    public fun user(block: RequestMessagePartsBuilder.() -> Unit): PromptBuilder = apply {
        user(RequestMessagePartsBuilder().apply(block).build())
    }

    @JavaAPI
    public fun toolResult(part: MessagePart.Tool.Result): PromptBuilder = apply {
        user { toolResult(part) }
    }

    @JavaAPI
    @JvmOverloads
    public fun toolResult(
        tool: String,
        output: String,
        id: String? = null,
        isError: Boolean = false,
        cacheControl: CacheControl? = null,
    ): PromptBuilder = apply {
        user { toolResult(MessagePart.Tool.Result(id, tool, output, isError, cacheControl)) }
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
        messages.add(
            Message.Assistant(
                content = content,
                metaInfo = ResponseMetaInfo.create(clock),
            )
        )
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
     * @param id Optional unique identifier for the message.
     */
    @JavaAPI
    public fun assistant(
        parts: List<MessagePart.ResponsePart>,
        finishReason: String? = null,
        rawResponse: JsonObject? = null,
        id: String? = null,
    ): PromptBuilder = apply {
        messages.add(Message.Assistant(parts, ResponseMetaInfo.create(clock), finishReason, rawResponse, id))
    }

    /**
     * Adds an assistant message to the prompt using a TextContentBuilder.
     *
     * @param init The initialization block for the TextContentBuilder
     */
    @JavaAPI
    public fun assistant(
        finishReason: String? = null,
        rawResponse: JsonObject? = null,
        id: String? = null,
        init: ResponseMessagePartsBuilder.() -> Unit
    ): PromptBuilder = apply {
        assistant(ResponseMessagePartsBuilder().apply(init).build(), finishReason, rawResponse, id)
    }

    @JavaAPI
    public fun reasoning(part: MessagePart.Reasoning): PromptBuilder = apply {
        assistant { reasoning(part) }
    }

    @JavaAPI
    @JvmOverloads
    public fun reasoning(
        content: String,
        id: String? = null,
        summary: String? = null,
        encrypted: String? = null,
        cacheControl: CacheControl? = null,
    ): PromptBuilder = apply {
        assistant {
            reasoning(
                MessagePart.Reasoning(
                    content = content,
                    summary = summary?.let { listOf(it) },
                    encrypted = encrypted,
                    id = id,
                    cacheControl = cacheControl,
                )
            )
        }
    }

    @JavaAPI
    public fun toolCall(part: MessagePart.Tool.Call): PromptBuilder = apply {
        assistant { toolCall(part) }
    }

    @JavaAPI
    @JvmOverloads
    public fun toolCall(
        tool: String,
        args: String,
        id: String? = null,
        cacheControl: CacheControl? = null,
    ): PromptBuilder = apply {
        assistant {
            toolCall(
                MessagePart.Tool.Call(
                    id = id,
                    tool = tool,
                    args = args,
                    cacheControl = cacheControl
                )
            )
        }
    }

    @JavaAPI
    @JvmOverloads
    public fun toolCall(
        tool: String,
        args: JsonObject,
        id: String? = null,
        cacheControl: CacheControl? = null,
    ): PromptBuilder = apply {
        assistant {
            toolCall(
                MessagePart.Tool.Call(
                    id = id,
                    tool = tool,
                    args = args,
                    cacheControl = cacheControl
                )
            )
        }
    }

    /**
     * Adds an assistant message to the prompt using a TextContentBuilder.
     *
     * @param init The initialization block for the TextContentBuilder
     */
    @JavaAPI
    public fun assistant(
        init: ResponseMessagePartsBuilder.() -> Unit
    ): PromptBuilder = apply {
        assistant(ResponseMessagePartsBuilder().apply(init).build(), null, null, null)
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
     * Builds and returns a Prompt object from the current state of the builder.
     *
     * @return A new Prompt object
     */
    @JavaAPI
    public fun build(): Prompt = Prompt(messages.toList(), id, params)
}
