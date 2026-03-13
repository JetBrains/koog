package ai.koog.prompt.message

import ai.koog.agents.annotations.JavaAPI
import ai.koog.utils.time.toKotlinInstant
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant
import java.time.Instant as JavaInstant

/**
 * Builder for creating [RequestMetaInfo] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * RequestMetaInfo metaInfo = RequestMetaInfo.builder()
 *     .timestamp(Instant.now())
 *     .metadata(jsonObject)
 *     .build();
 * ```
 */
@JavaAPI
public class RequestMetaInfoBuilder {
    private var timestamp: Instant? = null
    private var metadata: JsonObject? = null

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): RequestMetaInfoBuilder = apply {
        this.timestamp = timestamp.toKotlinInstant()
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): RequestMetaInfoBuilder = apply {
        this.timestamp = timestamp
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): RequestMetaInfoBuilder = apply {
        this.metadata = metadata
    }

    /**
     * Builds a new [RequestMetaInfo] instance.
     * If no timestamp is set, the current system time is used.
     */
    public fun build(): RequestMetaInfo = RequestMetaInfo(
        timestamp = timestamp ?: Clock.System.now(),
        metadata = metadata
    )
}

/**
 * Creates a new [RequestMetaInfoBuilder].
 */
@JavaAPI
public fun RequestMetaInfo.Companion.builder(): RequestMetaInfoBuilder = RequestMetaInfoBuilder()

/**
 * Creates a [RequestMetaInfo] with a [java.time.Instant] timestamp.
 */
@JavaAPI
public fun RequestMetaInfo.Companion.fromJavaInstant(
    timestamp: JavaInstant,
    metadata: JsonObject? = null
): RequestMetaInfo = RequestMetaInfo(
    timestamp = timestamp.toKotlinInstant(),
    metadata = metadata
)

/**
 * Builder for creating [ResponseMetaInfo] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * ResponseMetaInfo metaInfo = ResponseMetaInfo.builder()
 *     .timestamp(Instant.now())
 *     .totalTokensCount(100)
 *     .inputTokensCount(40)
 *     .outputTokensCount(60)
 *     .build();
 * ```
 */
@JavaAPI
public class ResponseMetaInfoBuilder {
    private var timestamp: Instant? = null
    private var totalTokensCount: Int? = null
    private var inputTokensCount: Int? = null
    private var outputTokensCount: Int? = null
    private var metadata: JsonObject? = null

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): ResponseMetaInfoBuilder = apply {
        this.timestamp = timestamp.toKotlinInstant()
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): ResponseMetaInfoBuilder = apply {
        this.timestamp = timestamp
    }

    /**
     * Sets the total token count.
     */
    public fun totalTokensCount(count: Int): ResponseMetaInfoBuilder = apply {
        this.totalTokensCount = count
    }

    /**
     * Sets the input token count.
     */
    public fun inputTokensCount(count: Int): ResponseMetaInfoBuilder = apply {
        this.inputTokensCount = count
    }

    /**
     * Sets the output token count.
     */
    public fun outputTokensCount(count: Int): ResponseMetaInfoBuilder = apply {
        this.outputTokensCount = count
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): ResponseMetaInfoBuilder = apply {
        this.metadata = metadata
    }

    /**
     * Builds a new [ResponseMetaInfo] instance.
     * If no timestamp is set, the current system time is used.
     */
    public fun build(): ResponseMetaInfo = ResponseMetaInfo(
        timestamp = timestamp ?: Clock.System.now(),
        totalTokensCount = totalTokensCount,
        inputTokensCount = inputTokensCount,
        outputTokensCount = outputTokensCount,
        metadata = metadata
    )
}

/**
 * Creates a new [ResponseMetaInfoBuilder].
 */
@JavaAPI
public fun ResponseMetaInfo.Companion.builder(): ResponseMetaInfoBuilder = ResponseMetaInfoBuilder()

/**
 * Creates a [ResponseMetaInfo] with a [java.time.Instant] timestamp.
 */
@JavaAPI
public fun ResponseMetaInfo.Companion.fromJavaInstant(
    timestamp: JavaInstant,
    totalTokensCount: Int? = null,
    inputTokensCount: Int? = null,
    outputTokensCount: Int? = null,
    metadata: JsonObject? = null
): ResponseMetaInfo = ResponseMetaInfo(
    timestamp = timestamp.toKotlinInstant(),
    totalTokensCount = totalTokensCount,
    inputTokensCount = inputTokensCount,
    outputTokensCount = outputTokensCount,
    metadata = metadata
)

/**
 * Builder for creating [Message.User] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * Message.User message = MessageBuilder.user()
 *     .content("Hello, world!")
 *     .timestamp(Instant.now())
 *     .build();
 * ```
 */
@JavaAPI
public class UserMessageBuilder {
    private val parts: MutableList<ContentPart> = mutableListOf()
    private val metaInfoBuilder: RequestMetaInfoBuilder = RequestMetaInfoBuilder()

    /**
     * Sets a single text content for the message, replacing any previously added parts.
     */
    public fun content(content: String): UserMessageBuilder = apply {
        parts.clear()
        parts.add(ContentPart.Text(content))
    }

    /**
     * Adds a content part to the message.
     */
    public fun addPart(part: ContentPart): UserMessageBuilder = apply {
        parts.add(part)
    }

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): UserMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): UserMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): UserMessageBuilder = apply {
        metaInfoBuilder.metadata(metadata)
    }

    /**
     * Builds a new [Message.User] instance.
     *
     * @throws IllegalStateException if no content parts have been added.
     */
    public fun build(): Message.User {
        check(parts.isNotEmpty()) { "User message must have at least one content part" }
        return Message.User(parts = parts.toList(), metaInfo = metaInfoBuilder.build())
    }
}

/**
 * Builder for creating [Message.Assistant] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * Message.Assistant message = MessageBuilder.assistant()
 *     .content("Hello!")
 *     .timestamp(Instant.now())
 *     .finishReason("stop")
 *     .build();
 * ```
 */
@JavaAPI
public class AssistantMessageBuilder {
    private val parts: MutableList<ContentPart> = mutableListOf()
    private val metaInfoBuilder: ResponseMetaInfoBuilder = ResponseMetaInfoBuilder()
    private var finishReason: String? = null

    /**
     * Sets a single text content for the message, replacing any previously added parts.
     */
    public fun content(content: String): AssistantMessageBuilder = apply {
        parts.clear()
        parts.add(ContentPart.Text(content))
    }

    /**
     * Adds a content part to the message.
     */
    public fun addPart(part: ContentPart): AssistantMessageBuilder = apply {
        parts.add(part)
    }

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): AssistantMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): AssistantMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the total token count.
     */
    public fun totalTokensCount(count: Int): AssistantMessageBuilder = apply {
        metaInfoBuilder.totalTokensCount(count)
    }

    /**
     * Sets the input token count.
     */
    public fun inputTokensCount(count: Int): AssistantMessageBuilder = apply {
        metaInfoBuilder.inputTokensCount(count)
    }

    /**
     * Sets the output token count.
     */
    public fun outputTokensCount(count: Int): AssistantMessageBuilder = apply {
        metaInfoBuilder.outputTokensCount(count)
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): AssistantMessageBuilder = apply {
        metaInfoBuilder.metadata(metadata)
    }

    /**
     * Sets the finish reason.
     */
    public fun finishReason(finishReason: String?): AssistantMessageBuilder = apply {
        this.finishReason = finishReason
    }

    /**
     * Builds a new [Message.Assistant] instance.
     *
     * @throws IllegalStateException if no content parts have been added.
     */
    public fun build(): Message.Assistant {
        check(parts.isNotEmpty()) { "Assistant message must have at least one content part" }
        return Message.Assistant(
            parts = parts.toList(),
            metaInfo = metaInfoBuilder.build(),
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
 *     .content("You are a helpful assistant.")
 *     .build();
 * ```
 */
@JavaAPI
public class SystemMessageBuilder {
    private val parts: MutableList<ContentPart.Text> = mutableListOf()
    private val metaInfoBuilder: RequestMetaInfoBuilder = RequestMetaInfoBuilder()

    /**
     * Sets a single text content for the message, replacing any previously added parts.
     */
    public fun content(content: String): SystemMessageBuilder = apply {
        parts.clear()
        parts.add(ContentPart.Text(content))
    }

    /**
     * Adds a text content part to the message.
     */
    public fun addPart(part: ContentPart.Text): SystemMessageBuilder = apply {
        parts.add(part)
    }

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): SystemMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): SystemMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): SystemMessageBuilder = apply {
        metaInfoBuilder.metadata(metadata)
    }

    /**
     * Builds a new [Message.System] instance.
     *
     * @throws IllegalStateException if no content parts have been added.
     */
    public fun build(): Message.System {
        check(parts.isNotEmpty()) { "System message must have at least one content part" }
        return Message.System(parts = parts.toList(), metaInfo = metaInfoBuilder.build())
    }
}

/**
 * Builder for creating [Message.Tool.Call] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * Message.Tool.Call message = MessageBuilder.toolCall()
 *     .id("call_123")
 *     .tool("search")
 *     .content("{\"query\": \"hello\"}")
 *     .build();
 * ```
 */
@JavaAPI
public class ToolCallMessageBuilder {
    private var id: String? = null
    private var tool: String? = null
    private val parts: MutableList<ContentPart.Text> = mutableListOf()
    private val metaInfoBuilder: ResponseMetaInfoBuilder = ResponseMetaInfoBuilder()

    /**
     * Sets the tool call ID.
     */
    public fun id(id: String?): ToolCallMessageBuilder = apply { this.id = id }

    /**
     * Sets the tool name.
     */
    public fun tool(tool: String): ToolCallMessageBuilder = apply { this.tool = tool }

    /**
     * Sets a single text content for the message, replacing any previously added parts.
     */
    public fun content(content: String): ToolCallMessageBuilder = apply {
        parts.clear()
        parts.add(ContentPart.Text(content))
    }

    /**
     * Adds a text content part to the message.
     */
    public fun addPart(part: ContentPart.Text): ToolCallMessageBuilder = apply {
        parts.add(part)
    }

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): ToolCallMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): ToolCallMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the total token count.
     */
    public fun totalTokensCount(count: Int): ToolCallMessageBuilder = apply {
        metaInfoBuilder.totalTokensCount(count)
    }

    /**
     * Sets the input token count.
     */
    public fun inputTokensCount(count: Int): ToolCallMessageBuilder = apply {
        metaInfoBuilder.inputTokensCount(count)
    }

    /**
     * Sets the output token count.
     */
    public fun outputTokensCount(count: Int): ToolCallMessageBuilder = apply {
        metaInfoBuilder.outputTokensCount(count)
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): ToolCallMessageBuilder = apply {
        metaInfoBuilder.metadata(metadata)
    }

    /**
     * Builds a new [Message.Tool.Call] instance.
     *
     * @throws IllegalStateException if tool name or content parts are missing.
     */
    public fun build(): Message.Tool.Call {
        checkNotNull(tool) { "Tool name must be set" }
        check(parts.isNotEmpty()) { "Tool call message must have at least one content part" }
        return Message.Tool.Call(
            id = id,
            tool = tool!!,
            parts = parts.toList(),
            metaInfo = metaInfoBuilder.build()
        )
    }
}

/**
 * Builder for creating [Message.Tool.Result] instances from Java code.
 *
 * Usage from Java:
 * ```java
 * Message.Tool.Result message = MessageBuilder.toolResult()
 *     .id("call_123")
 *     .tool("search")
 *     .content("Found 5 results")
 *     .build();
 * ```
 */
@JavaAPI
public class ToolResultMessageBuilder {
    private var id: String? = null
    private var tool: String? = null
    private val parts: MutableList<ContentPart.Text> = mutableListOf()
    private val metaInfoBuilder: RequestMetaInfoBuilder = RequestMetaInfoBuilder()

    /**
     * Sets the tool result ID.
     */
    public fun id(id: String?): ToolResultMessageBuilder = apply { this.id = id }

    /**
     * Sets the tool name.
     */
    public fun tool(tool: String): ToolResultMessageBuilder = apply { this.tool = tool }

    /**
     * Sets a single text content for the message, replacing any previously added parts.
     */
    public fun content(content: String): ToolResultMessageBuilder = apply {
        parts.clear()
        parts.add(ContentPart.Text(content))
    }

    /**
     * Adds a text content part to the message.
     */
    public fun addPart(part: ContentPart.Text): ToolResultMessageBuilder = apply {
        parts.add(part)
    }

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): ToolResultMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): ToolResultMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): ToolResultMessageBuilder = apply {
        metaInfoBuilder.metadata(metadata)
    }

    /**
     * Builds a new [Message.Tool.Result] instance.
     *
     * @throws IllegalStateException if tool name or content parts are missing.
     */
    public fun build(): Message.Tool.Result {
        checkNotNull(tool) { "Tool name must be set" }
        check(parts.isNotEmpty()) { "Tool result message must have at least one content part" }
        return Message.Tool.Result(
            id = id,
            tool = tool!!,
            parts = parts.toList(),
            metaInfo = metaInfoBuilder.build()
        )
    }
}

/**
 * Builder for creating [Message.Reasoning] instances from Java code.
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
public class ReasoningMessageBuilder {
    private var id: String? = null
    private var encrypted: String? = null
    private val parts: MutableList<ContentPart.Text> = mutableListOf()
    private var summary: List<ContentPart.Text>? = null
    private val metaInfoBuilder: ResponseMetaInfoBuilder = ResponseMetaInfoBuilder()

    /**
     * Sets the reasoning ID.
     */
    public fun id(id: String?): ReasoningMessageBuilder = apply { this.id = id }

    /**
     * Sets the encrypted content.
     */
    public fun encrypted(encrypted: String?): ReasoningMessageBuilder = apply { this.encrypted = encrypted }

    /**
     * Sets a single text content for the message, replacing any previously added parts.
     */
    public fun content(content: String): ReasoningMessageBuilder = apply {
        parts.clear()
        parts.add(ContentPart.Text(content))
    }

    /**
     * Adds a text content part to the message.
     */
    public fun addPart(part: ContentPart.Text): ReasoningMessageBuilder = apply {
        parts.add(part)
    }

    /**
     * Sets the summary as a single text string.
     */
    public fun summary(summary: String): ReasoningMessageBuilder = apply {
        this.summary = listOf(ContentPart.Text(summary))
    }

    /**
     * Sets the summary as a list of text parts.
     */
    public fun summary(summary: List<ContentPart.Text>?): ReasoningMessageBuilder = apply {
        this.summary = summary
    }

    /**
     * Sets the timestamp using [java.time.Instant].
     */
    public fun timestamp(timestamp: JavaInstant): ReasoningMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the timestamp using [kotlin.time.Instant].
     */
    public fun timestamp(timestamp: Instant): ReasoningMessageBuilder = apply {
        metaInfoBuilder.timestamp(timestamp)
    }

    /**
     * Sets the total token count.
     */
    public fun totalTokensCount(count: Int): ReasoningMessageBuilder = apply {
        metaInfoBuilder.totalTokensCount(count)
    }

    /**
     * Sets the input token count.
     */
    public fun inputTokensCount(count: Int): ReasoningMessageBuilder = apply {
        metaInfoBuilder.inputTokensCount(count)
    }

    /**
     * Sets the output token count.
     */
    public fun outputTokensCount(count: Int): ReasoningMessageBuilder = apply {
        metaInfoBuilder.outputTokensCount(count)
    }

    /**
     * Sets the metadata.
     */
    public fun metadata(metadata: JsonObject?): ReasoningMessageBuilder = apply {
        metaInfoBuilder.metadata(metadata)
    }

    /**
     * Builds a new [Message.Reasoning] instance.
     *
     * @throws IllegalStateException if no content parts have been added.
     */
    public fun build(): Message.Reasoning {
        check(parts.isNotEmpty()) { "Reasoning message must have at least one content part" }
        return Message.Reasoning(
            id = id,
            encrypted = encrypted,
            parts = parts.toList(),
            summary = summary,
            metaInfo = metaInfoBuilder.build()
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
 * Message.Tool.Call toolCall = MessageBuilder.toolCall()
 *     .id("call_123")
 *     .tool("search")
 *     .content("{\"query\": \"hello\"}")
 *     .build();
 *
 * Message.Tool.Result toolResult = MessageBuilder.toolResult()
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

    /**
     * Creates a new [ToolCallMessageBuilder] for building [Message.Tool.Call] instances.
     */
    @JvmStatic
    public fun toolCall(): ToolCallMessageBuilder = ToolCallMessageBuilder()

    /**
     * Creates a new [ToolResultMessageBuilder] for building [Message.Tool.Result] instances.
     */
    @JvmStatic
    public fun toolResult(): ToolResultMessageBuilder = ToolResultMessageBuilder()

    /**
     * Creates a new [ReasoningMessageBuilder] for building [Message.Reasoning] instances.
     */
    @JvmStatic
    public fun reasoning(): ReasoningMessageBuilder = ReasoningMessageBuilder()
}
