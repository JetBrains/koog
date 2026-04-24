package ai.koog.prompt.executor.clients.retrollmfit

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

// ─── Format enums ────────────────────────────────────────────────────────────

/**
 * Controls how [ai.koog.prompt.dsl.Prompt] messages are serialized into the request field.
 *
 * - [FLAT_STRING]  — all messages joined as one plain-text string (for `@PromptField`).
 * - [OPENAI_CHAT]  — `[{"role":"…","content":"…"}]` array (OpenAI / compatible APIs).
 */
public enum class MessageFormat { FLAT_STRING, OPENAI_CHAT }

/**
 * Wire format for tool definitions (`@ToolsField`) and tool-call extraction (`@ResponseToolCalls`).
 *
 * - [OPENAI]    — `{"type":"function","function":{"name":…,"description":…,"parameters":{…}}}`
 * - [ANTHROPIC] — `{"name":…,"description":…,"input_schema":{…}}`
 */
public enum class ToolFormat { OPENAI, ANTHROPIC }

// ─── Endpoint ─────────────────────────────────────────────────────────────────

/**
 * Marks a data class as an LLM endpoint descriptor.
 *
 * ### Flat API (simple):
 * ```kotlin
 * @LLMEndpoint(url = "https://my-server/api/prompt", authHeaderName = "X-User-Id", authHeaderValue = "…")
 * @Serializable
 * data class JaikaRequest(@PromptField val prompt: String, val stream: Boolean = false)
 * ```
 *
 * ### Chat API (OpenAI-compatible):
 * ```kotlin
 * @LLMEndpoint(url = "https://api.openai.com/v1/chat/completions",
 *              authHeaderName = "Authorization", authHeaderValue = "Bearer $KEY")
 * @Serializable
 * data class ChatRequest(
 *     @ModelField      val model: String = "gpt-4o",
 *     @MessagesField   val messages: List<JsonObject> = emptyList(),
 *     @ToolsField      val tools: List<JsonObject>? = null,
 *     @StreamField     val stream: Boolean = false,
 * )
 * ```
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class LLMEndpoint(
    val url: String,
    val authHeaderName: String = "",
    val authHeaderValue: String = "",
)

// ─── Request-field annotations ───────────────────────────────────────────────

/** Injects the **flattened prompt string** (all messages joined). For flat APIs like `{"prompt":"…"}`. */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class PromptField

/**
 * Injects the **messages array** converted to [format] into a `List<JsonObject>` field.
 * The framework maps every [ai.koog.prompt.message.Message] in the prompt to the correct wire shape.
 */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class MessagesField(val format: MessageFormat = MessageFormat.OPENAI_CHAT)

/** Injects the **model identifier** (`LLModel.id`) into a `String` field. */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class ModelField

/**
 * Injects **tool definitions** (converted from `List<ToolDescriptor>`) into a `List<JsonObject>?` field.
 * Left as its default (usually `null`) when `execute()` is called with no tools.
 */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class ToolsField(val format: ToolFormat = ToolFormat.OPENAI)

/**
 * Marks the `Boolean` field that enables **server-sent-event streaming**.
 * Set to `true` by `executeStreaming()`, `false` by `execute()`.
 */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class StreamField

// ─── Response-field annotations ──────────────────────────────────────────────

/**
 * Marks a flat `String` field in the response class as the assistant reply.
 * For flat APIs (`{"text":"…"}`). For nested paths use the class-level [@ResponseText].
 */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class ResponseTextField

// ─── Response class-level annotations (JSON path) ────────────────────────────

/**
 * **Class-level** dot-notation JSON path to the **assistant text** in the response.
 *
 * Supports array indexing: `"choices[0].message.content"`.
 * Use this when the text is nested rather than top-level.
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class ResponseText(val path: String)

/**
 * **Class-level** path to the `finish_reason` (or equivalent) field.
 *
 * When the resolved value is `"tool_calls"` the framework parses tool calls instead of text.
 * Example: `"choices[0].finish_reason"`
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class ResponseFinishReason(val path: String)

/**
 * **Class-level** path to the **tool-calls array** in the response, parsed using [format].
 *
 * Example: `"choices[0].message.tool_calls"` with [ToolFormat.OPENAI]
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class ResponseToolCalls(val path: String, val format: ToolFormat = ToolFormat.OPENAI)

/**
 * **Class-level** path to the **text delta** inside a streaming chunk.
 *
 * Each SSE frame is decoded as the response class; the framework resolves this path and emits
 * a [ai.koog.prompt.streaming.StreamFrame.TextDelta].
 * Example: `"choices[0].delta.content"`
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class ResponseStreamDelta(val path: String)
