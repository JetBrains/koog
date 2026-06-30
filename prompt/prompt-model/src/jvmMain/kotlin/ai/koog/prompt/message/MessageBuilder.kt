package ai.koog.prompt.message

import ai.koog.agents.annotations.JavaAPI
import kotlinx.serialization.json.JsonObject

/**
 * Builder for creating [Message.User] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * Message.User message = MessageBuilder.user()
 *     .addText("Hello, world!")
 *     .metaInfo(RequestMetaInfoBuilder.timestamp(Instant.now()).build())
 *     .build();
 * ```
 */
@JavaAPI
public class UserMessageBuilder {
    private var id: String? = null
    private val parts: MutableList<MessagePart.RequestPart> = mutableListOf()
    private var metaInfo: RequestMetaInfo = RequestMetaInfo.Empty

    public fun id(id: String?): UserMessageBuilder = apply { this.id = id }

    public fun addPart(part: MessagePart.RequestPart): UserMessageBuilder = apply {
        parts.add(part)
    }

    @JvmOverloads
    public fun addText(content: String, cache: CacheControl? = null): UserMessageBuilder = apply {
        addPart(MessagePart.Text(content, cache))
    }

    @JvmOverloads
    public fun addAttachment(attachment: AttachmentSource, cache: CacheControl? = null): UserMessageBuilder = apply {
        addPart(MessagePart.Attachment(attachment, cache))
    }

    public fun addToolResult(toolResult: MessagePart.Tool.Result): UserMessageBuilder = apply {
        addPart(toolResult)
    }

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun metaInfo(metaInfo: RequestMetaInfo): UserMessageBuilder = apply {
        this.metaInfo = metaInfo
    }

    /**
     * Builds a new [Message.User] instance.
     *
     * @throws IllegalStateException if no content parts have been added.
     */
    public fun build(): Message.User {
        check(parts.isNotEmpty()) { "User message must have at least one content part" }
        return Message.User(
            id = id,
            parts = parts,
            metaInfo = metaInfo
        )
    }
}

/**
 * Builder for creating [Message.Assistant] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * Message.Assistant message = MessageBuilder.assistant()
 *     .content("Hello!")
 *     .metaInfo(ResponseMetaInfoBuilder.timestamp(Instant.now()).build())
 *     .finishReason("stop")
 *     .build();
 * ```
 */
@JavaAPI
public class AssistantMessageBuilder {
    private var id: String? = null
    private val parts: MutableList<MessagePart.ResponsePart> = mutableListOf()
    private var metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty
    private var finishReason: String? = null

    public fun id(id: String?): AssistantMessageBuilder = apply { this.id = id }

    public fun addPart(part: MessagePart.ResponsePart): AssistantMessageBuilder = apply {
        parts.add(part)
    }

    public fun addReasoning(toolResult: MessagePart.Reasoning): AssistantMessageBuilder = apply {
        addPart(toolResult)
    }

    @JvmOverloads
    public fun addText(content: String, cache: CacheControl? = null): AssistantMessageBuilder = apply {
        addPart(MessagePart.Text(content, cache))
    }

    public fun addToolCall(toolCall: MessagePart.Tool.Call): AssistantMessageBuilder = apply {
        addPart(toolCall)
    }

    /**
     * Sets the finish reason.
     */
    public fun finishReason(finishReason: String): AssistantMessageBuilder = apply {
        this.finishReason = finishReason
    }

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun metaInfo(metaInfo: ResponseMetaInfo): AssistantMessageBuilder = apply {
        this.metaInfo = metaInfo
    }

    /**
     * Builds a new [Message.Assistant] instance.
     *
     * @throws IllegalStateException if no content parts have been added.
     */
    public fun build(): Message.Assistant {
        check(parts.isNotEmpty()) { "Assistant message must have at least one content part" }
        return Message.Assistant(
            id = id,
            parts = parts.toList(),
            metaInfo = metaInfo,
            finishReason = finishReason
        )
    }
}

/**
 * Builder for creating [Message.System] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * Message.System message = MessageBuilder.system()
 *     .setText("You are a helpful assistant.")
 *     .build();
 * ```
 */
@JavaAPI
public class SystemMessageBuilder {
    private var id: String? = null
    private val parts: MutableList<MessagePart.Text> = mutableListOf()
    private var metaInfo: RequestMetaInfo = RequestMetaInfo.Empty

    public fun id(id: String?): SystemMessageBuilder = apply { this.id = id }

    /**
     * Adds the text content of the message, replacing any previously added parts.
     */
    @JvmOverloads
    public fun addText(content: String, cache: CacheControl? = null): SystemMessageBuilder = apply {
        parts.add(MessagePart.Text(content, cache))
    }

    /**
     * Adds a text content part to the message.
     */
    public fun addPart(part: MessagePart.Text): SystemMessageBuilder = apply {
        parts.add(part)
    }

    public fun metaInfo(metaInfo: RequestMetaInfo): SystemMessageBuilder = apply {
        this.metaInfo = metaInfo
    }

    /**
     * Builds a new [Message.System] instance.
     *
     * @throws IllegalStateException if no content parts have been added.
     */
    public fun build(): Message.System {
        check(parts.isNotEmpty()) { "System message must have at least one content part" }
        return Message.System(
            id = id,
            parts = parts,
            metaInfo = metaInfo
        )
    }
}

/**
 * Builder for creating [MessagePart.Tool.Call] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * MessagePart.Tool.Call message = MessageBuilder.toolCall()
 *     .id("call_123")
 *     .tool("search")
 *     .content("{\"query\": \"hello\"}")
 *     .build();
 * ```
 */
@JavaAPI
public class ToolCallBuilder {
    private var id: String? = null
    private var tool: String? = null
    private var args: JsonObject? = null
    private var cacheControl: CacheControl? = null

    /**
     * Sets the tool call ID.
     */
    public fun id(id: String?): ToolCallBuilder = apply { this.id = id }

    /**
     * Sets the tool name.
     */
    public fun tool(tool: String): ToolCallBuilder = apply { this.tool = tool }

    /**
     * Sets the tool call arguments.
     */
    public fun args(args: JsonObject): ToolCallBuilder = apply { this.args = args }

    /**
     * Sets the cache-control directive for the provider's prompt-caching feature.
     */
    public fun cacheControl(cacheControl: CacheControl?): ToolCallBuilder = apply { this.cacheControl = cacheControl }

    /**
     * Builds a new [MessagePart.Tool.Call] instance.
     *
     * @throws IllegalStateException if tool name or content parts are missing.
     */
    public fun build(): MessagePart.Tool.Call {
        checkNotNull(tool) { "Tool name must be set" }
        checkNotNull(args) { "Tool call message must have args" }
        return MessagePart.Tool.Call(
            id = id,
            tool = tool!!,
            args = args!!,
            cacheControl = cacheControl,
        )
    }
}

/**
 * Builder for creating [MessagePart.Tool.Result] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * MessagePart.Tool.Result message = MessageBuilder.toolResult()
 *     .id("call_123")
 *     .tool("search")
 *     .content("Found 5 results")
 *     .build();
 * ```
 */
@JavaAPI
public class ToolResultBuilder {
    private var id: String? = null
    private var tool: String? = null
    private var output: String? = null
    private var isError: Boolean = false
    private var cacheControl: CacheControl? = null

    /**
     * Sets the tool result ID.
     */
    public fun id(id: String?): ToolResultBuilder = apply { this.id = id }

    /**
     * Sets the tool name.
     */
    public fun tool(tool: String): ToolResultBuilder = apply { this.tool = tool }

    /**
     * Adds a text content part to the message.
     */
    public fun output(output: String): ToolResultBuilder = apply { this.output = output }

    public fun isError(isError: Boolean): ToolResultBuilder = apply { this.isError = isError }

    /**
     * Sets the cache-control directive for the provider's prompt-caching feature.
     */
    public fun cacheControl(cacheControl: CacheControl?): ToolResultBuilder = apply { this.cacheControl = cacheControl }

    /**
     * Builds a new [MessagePart.Tool.Result] instance.
     *
     * @throws IllegalStateException if tool name or content parts are missing.
     */
    public fun build(): MessagePart.Tool.Result {
        checkNotNull(tool) { "Tool name must be set" }
        checkNotNull(output) { "Tool result message must have output" }
        return MessagePart.Tool.Result(
            id = id,
            tool = tool!!,
            output = output!!,
            isError = isError,
            cacheControl = cacheControl
        )
    }
}

/**
 * Builder for creating [MessagePart.Reasoning] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * Message.Reasoning message = MessageBuilder.reasoning()
 *     .content("Let me think about this...")
 *     .summary("Thinking about the problem")
 *     .build();
 * ```
 */
@JavaAPI
public class ReasoningBuilder {
    private var id: String? = null
    private val content: MutableList<String> = mutableListOf()
    private var summary: List<String>? = null
    private var encrypted: String? = null
    private var cacheControl: CacheControl? = null

    /**
     * Sets the reasoning ID.
     */
    public fun id(id: String?): ReasoningBuilder = apply { this.id = id }

    /**
     * Sets the encrypted content.
     */
    public fun encrypted(encrypted: String?): ReasoningBuilder = apply { this.encrypted = encrypted }

    /**
     * Sets the cache-control directive for the provider's prompt-caching feature.
     */
    public fun cacheControl(cacheControl: CacheControl?): ReasoningBuilder = apply { this.cacheControl = cacheControl }

    /**
     * Sets a single text content for the message, replacing any previously added parts.
     */
    public fun content(content: List<String>): ReasoningBuilder = apply {
        this.content.clear()
        this.content.addAll(content)
    }

    /**
     * Sets the summary as a single text string.
     */
    public fun summary(summary: List<String>): ReasoningBuilder = apply {
        this.summary = summary
    }

    /**
     * Builds a new [MessagePart.Reasoning] instance.
     *
     * @throws IllegalStateException if no content parts have been added.
     */
    public fun build(): MessagePart.Reasoning {
        check(content.isNotEmpty()) { "Reasoning message must have at least one content part" }
        return MessagePart.Reasoning(
            id = id,
            content = content,
            summary = summary,
            encrypted = encrypted,
            cacheControl = cacheControl,
        )
    }
}

/**
 * Entry point for creating [Message] instances from Java code using the builder pattern.
 *
 * Usage from Java:
 * ```java
 * Message.User userMsg = MessageBuilder.user()
 *     .content("Hello!")
 *     .timestamp(Instant.now())
 *     .build();
 *
 * Message.Assistant assistantMsg = MessageBuilder.assistant()
 *     .content("Hi there!")
 *     .finishReason("stop")
 *     .build();
 *
 * Message.System systemMsg = MessageBuilder.system()
 *     .content("You are a helpful assistant.")
 *     .build();
 *
 * MessagePart.Tool.Call toolCall = MessageBuilder.toolCall()
 *     .id("call_123")
 *     .tool("search")
 *     .content("{\"query\": \"hello\"}")
 *     .build();
 *
 * MessagePart.Tool.Result toolResult = MessageBuilder.toolResult()
 *     .id("call_123")
 *     .tool("search")
 *     .content("Found 5 results")
 *     .build();
 *
 * Message.Reasoning reasoning = MessageBuilder.reasoning()
 *     .content("Let me think...")
 *     .summary("Thinking")
 *     .build();
 * ```
 */
@JavaAPI
public object MessageBuilder {

    /**
     * Creates a new [UserMessageBuilder] for building [Message.User] instances.
     */
    @JvmStatic
    public fun user(): UserMessageBuilder = UserMessageBuilder()

    /**
     * Creates a new [AssistantMessageBuilder] for building [Message.Assistant] instances.
     */
    @JvmStatic
    public fun assistant(): AssistantMessageBuilder = AssistantMessageBuilder()

    /**
     * Creates a new [SystemMessageBuilder] for building [Message.System] instances.
     */
    @JvmStatic
    public fun system(): SystemMessageBuilder = SystemMessageBuilder()
}
