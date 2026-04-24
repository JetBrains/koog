package ai.koog.prompt.executor.clients.retrollmfit

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.reflect.KClass

// ─── Format enum (tools only) ────────────────────────────────────────────────

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
 * Supports two authentication styles — pick one:
 * - **Header auth** (most APIs): set [authHeaderName] + [authHeaderValue].
 * - **Query param auth** (e.g. `?key=…`): set [authQueryParam] + [authQueryValue].
 *
 * The [url] may contain the `{model}` placeholder, replaced at call time with `LLModel.id`:
 * ```
 * url = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
 * ```
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
    val authQueryParam: String = "",
    val authQueryValue: String = "",
)

// ─── Request-field annotations ───────────────────────────────────────────────

/** Injects the **flattened prompt string** (all messages joined). For flat APIs like `{"prompt":"…"}`. */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class PromptField

/**
 * Injects the **messages array** into a `List<JsonObject>` field using [serializer].
 *
 * The default [OpenAIChatSerializer] produces `[{"role":"…","content":"…"}]`, compatible with
 * OpenAI, Anthropic, and any OpenAI-compatible API.
 *
 * For any other wire format, implement [MessagesSerializer] and pass your class:
 * ```kotlin
 * @MessagesField(GeminiMessagesSerializer::class)
 * val contents: List<JsonObject> = emptyList()
 * ```
 * The framework never needs to change — new API formats are handled purely in user code.
 */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class MessagesField(val serializer: KClass<out MessagesSerializer> = OpenAIChatSerializer::class)

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
 * **Class-level** path to the finish-reason (or equivalent) field.
 *
 * When the resolved value equals [toolCallValue] the framework routes to tool-call parsing
 * instead of text extraction.
 *
 * | Provider  | path                           | toolCallValue          |
 * |-----------|--------------------------------|------------------------|
 * | OpenAI    | `"choices[0].finish_reason"`   | `"tool_calls"` (default) |
 * | Anthropic | `"stop_reason"`                | `"tool_use"`           |
 * | Gemini    | `"candidates[0].finishReason"` | `"TOOL_USE"`           |
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class ResponseFinishReason(val path: String, val toolCallValue: String = "tool_calls")

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
