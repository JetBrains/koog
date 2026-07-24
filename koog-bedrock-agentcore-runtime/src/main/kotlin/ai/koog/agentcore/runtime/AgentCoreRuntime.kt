package ai.koog.agentcore.runtime

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.parseHeaderValue
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Well-known AgentCore HTTP headers used by Amazon Bedrock AgentCore Runtime.
 */
public object AgentCoreHeaders {
    // Core AgentCore Headers
    public const val SESSION_ID: String = "X-Amzn-Bedrock-AgentCore-Runtime-Session-Id"
    public const val USER_ID: String = "X-Amzn-Bedrock-AgentCore-Runtime-User-Id"
    public const val CUSTOM_HEADER_PREFIX: String = "X-Amzn-Bedrock-AgentCore-Runtime-Custom-"

    // Authentication & Authorization
    public const val AUTHORIZATION: String = "Authorization"
    public const val WORKLOAD_ACCESS_TOKEN: String = "workloadaccesstoken"
    public const val WORKLOAD_ACCESS_TOKEN_RUNTIME: String = "X-amzn-bedrock-agentcore-runtime-workload-accesstoken"
    public const val GUEST_AUTH: String = "X-aws-guest-auth"

    // AWS Infrastructure
    public const val REQUEST_ID: String = "X-amzn-requestid"
    public const val TRACE_ID: String = "X-amzn-trace-id"
    public const val BAGGAGE: String = "baggage"

    // Proxy Information
    public const val PROXY_IP: String = "X-aws-proxy-ip"
    public const val PROXY_PORT: String = "X-aws-proxy-port"
}

/**
 * Context object containing HTTP headers from AgentCore invocation requests.
 *
 * Provides access to all headers sent by the AgentCore Runtime, including session IDs,
 * user IDs, authentication tokens, and custom headers.
 */
public class AgentCoreContext(private val headers: Headers, public val taskTracker: AgentCoreTaskTracker) {
    /** Returns all headers from the invocation request. */
    public fun getHeaders(): Headers = headers

    /** Returns the value of a specific header, or null if not present. */
    public fun getHeader(headerName: String): String? = headers[headerName]
}

/**
 * Health status for AgentCore ping responses.
 *
 * - [HEALTHY] — Agent is ready, no background tasks. Runtime may scale down if idle.
 * - [HEALTHY_BUSY] — Agent is healthy but actively processing. Runtime keeps agent alive.
 * - [UNHEALTHY] — Agent has issues. Runtime may restart or replace agent.
 */
public enum class PingStatus(private val value: String) {
    HEALTHY("Healthy"),
    HEALTHY_BUSY("HealthyBusy"),
    UNHEALTHY("Unhealthy");

    override fun toString(): String = value
}

/** Exception thrown when there are issues with AgentCore invocation. */
public class AgentCoreInvocationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Tracks active background tasks so the `/ping` endpoint can report
 * [PingStatus.HEALTHY_BUSY] instead of [PingStatus.HEALTHY], preventing the runtime
 * from scaling the agent down while long-running work is in progress.
 */
public class AgentCoreTaskTracker {
    private val activeTasks = AtomicLong(0)

    public fun increment() {
        activeTasks.incrementAndGet()
    }

    public fun decrement() {
        activeTasks.updateAndGet { if (it > 0) it - 1 else 0 }
    }

    public fun getCount(): Long = activeTasks.get()
}

/** Ping response data returned by [AgentCorePingService]. */
public data class AgentCorePingResponse(
    val status: PingStatus,
    val httpStatus: HttpStatusCode,
    val timeOfLastUpdate: Long,
)

/** Interface for providing ping status. */
public fun interface AgentCorePingService {
    public fun getPingStatus(): AgentCorePingResponse
}

/**
 * Default ping service that uses [AgentCoreTaskTracker] for busy detection.
 *
 * Returns [PingStatus.HEALTHY] (no active tasks), [PingStatus.HEALTHY_BUSY] (active tasks),
 * or [PingStatus.UNHEALTHY] (any exception).
 */
public class StaticAgentCorePingService(private val taskTracker: AgentCoreTaskTracker) : AgentCorePingService {
    private val cachedResponse = AtomicReference<AgentCorePingResponse>()

    override fun getPingStatus(): AgentCorePingResponse {
        return try {
            if (taskTracker.getCount() > 0) {
                updateCachedResponse(PingStatus.HEALTHY_BUSY, HttpStatusCode.OK)
            } else {
                updateCachedResponse(PingStatus.HEALTHY, HttpStatusCode.OK)
            }
        } catch (_: Exception) {
            updateCachedResponse(PingStatus.UNHEALTHY, HttpStatusCode.InternalServerError)
        }
    }

    private fun updateCachedResponse(status: PingStatus, httpStatus: HttpStatusCode): AgentCorePingResponse {
        return cachedResponse.updateAndGet { current ->
            if (current == null || current.status != status) {
                AgentCorePingResponse(status, httpStatus, System.currentTimeMillis() / 1000)
            } else {
                current
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Sealed input / output types and the unified invocation handler
// ---------------------------------------------------------------------------

/**
 * Structured input handed to the unified invocation handler.
 *
 * The plugin auto-dispatches based on the request's `Content-Type` and `Content-Length`:
 *
 * - `application/json`, `text/...`                       → [Text]      (UTF-8 string)
 * - `multipart/...`                                      → [Multipart] ([MultiPartData])
 * - any other type within [binaryStreamThresholdBytes]    → [Binary]    (full bytes)
 * - any other type beyond the threshold or unknown length → [Stream]    ([ByteReadChannel])
 *
 * Handlers pattern-match on the variant they expect; raise an [AgentCoreInvocationException]
 * for unsupported shapes.
 */
public sealed interface InvocationInput {
    /** Request body decoded as a UTF-8 string (used for JSON/text content types). */
    public data class Text(val body: String) : InvocationInput

    /** Request body materialised as raw bytes plus the original `Content-Type`. */
    public class Binary(public val bytes: ByteArray, public val contentType: ContentType) : InvocationInput {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return bytes.contentEquals(other.bytes) && contentType == other.contentType
        }

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + contentType.hashCode()
    }

    /**
     * Request body as a [ByteReadChannel] for incremental reads. Use this for large uploads
     * (audio/video/large PDF) where allocating the full payload as a [ByteArray] is undesirable.
     */
    public class Stream(public val channel: ByteReadChannel, public val contentType: ContentType) : InvocationInput

    /**
     * `multipart/form-data` request — handler iterates over [MultiPartData]
     * to read individual file/field parts (`PartData.FormItem`, `PartData.FileItem`, etc.).
     * Useful for file uploads where metadata fields accompany binary blobs.
     */
    public class Multipart(public val parts: MultiPartData) : InvocationInput
}

/**
 * Structured result of an invocation. Each variant maps to a specific HTTP response shape.
 *
 * - [Text]         → plain text / JSON / single-event SSE depending on `Accept`.
 * - [Binary]       → raw bytes with the supplied `Content-Type` and known length.
 * - [TextStream]   → `text/event-stream` with each chunk written as its own `data:` event.
 * - [BinaryStream] → chunked binary with the supplied `Content-Type`.
 */
public sealed interface InvocationResult {
    public data class Text(val value: String) : InvocationResult

    public data class Binary(val bytes: ByteArray, val contentType: ContentType) : InvocationResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return bytes.contentEquals(other.bytes) && contentType == other.contentType
        }

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + contentType.hashCode()
    }

    /** Streaming text result (SSE `data:` events per chunk). */
    public class TextStream(public val chunks: Flow<String>) : InvocationResult

    /** Streaming binary result (HTTP chunked transfer-encoding). */
    public class BinaryStream(public val chunks: Flow<ByteArray>, public val contentType: ContentType) :
        InvocationResult
}

/**
 * Unified handler for invocation requests — single entry point for all input/output shapes.
 *
 * The plugin auto-dispatches the request body into an [InvocationInput] variant by
 * `Content-Type` and size. The handler returns an [InvocationResult] variant which the
 * plugin maps to the correct HTTP response (text, binary, SSE stream, or chunked binary).
 *
 * **Coroutine context warning for streaming results:** when constructing
 * [InvocationResult.TextStream] / [InvocationResult.BinaryStream] from a callback-style SDK
 * (e.g. AWS Bedrock's `converseStream`, whose collector runs in its own coroutine context),
 * use [streamingFlow] / [streamingBytesFlow] rather than `flow { emit() }` — the strict
 * `flow {}` builder will throw `IllegalStateException: Flow invariant is violated` when
 * emit and collect happen in different contexts.
 */
public typealias UnifiedInvocationHandler =
    suspend RoutingContext.(input: InvocationInput, context: AgentCoreContext) -> InvocationResult

/**
 * Helper that builds a context-safe [Flow]<[String]> suitable for [InvocationResult.TextStream].
 *
 * Use this when emitting items from a callback whose execution context differs from the Flow
 * collector's context (typical for AWS SDK streaming callbacks). Internally a [channelFlow] is
 * used so `send` calls cross coroutine contexts safely.
 */
public fun streamingFlow(
    producer: suspend SendChannel<String>.() -> Unit,
): Flow<String> = channelFlow { producer(this) }

/** Symmetric helper for binary streams used with [InvocationResult.BinaryStream]. */
public fun streamingBytesFlow(
    producer: suspend SendChannel<ByteArray>.() -> Unit,
): Flow<ByteArray> = channelFlow { producer(this) }

/**
 * Typed handler for invocation requests — convenience for the common JSON-in / JSON-out case.
 *
 * Receives the request body already deserialized into [I] and returns a value of type [O] that is
 * serialized back to the client. Both directions are handled by Ktor's `ContentNegotiation`
 * plugin, so the request `Content-Type` (e.g. `application/json`) and the response `Accept`
 * header drive the conversion. Requires `ContentNegotiation` (e.g. `json()`) to be installed.
 *
 * Registered via [handle].
 */
public typealias TypedInvocationHandler<I, O> =
    suspend RoutingContext.(input: I, context: AgentCoreContext) -> O

/**
 * Internal holder describing a typed invocation registered via [AgentCoreRuntimeConfig.handle].
 */
@PublishedApi
internal class TypedInvocation(
    val inputType: TypeInfo,
    val outputType: TypeInfo,
    val handle: suspend RoutingContext.(input: Any?, context: AgentCoreContext) -> Any?,
)

/** Configuration for the [agentCoreRuntime] route installer. */
public class AgentCoreRuntimeConfig {
    /**
     * Single handler covering all input/output shapes. Required unless a typed handler is
     * registered via [handle].
     */
    public var handler: UnifiedInvocationHandler? = null

    /**
     * Threshold (in bytes) above which an inbound request body is exposed as
     * [InvocationInput.Stream] rather than [InvocationInput.Binary]. Requests with no
     * `Content-Length` header are also exposed as [InvocationInput.Stream].
     *
     * Default: 1 MiB.
     */
    public var binaryStreamThresholdBytes: Long = 1L * 1024 * 1024

    /**
     * Maximum allowed request body size, in bytes. Requests whose `Content-Length` exceeds
     * this value are rejected with **HTTP 413 Payload Too Large** before the handler is
     * invoked. Requests with no `Content-Length` header are not pre-checked (the underlying
     * engine's limits still apply).
     *
     * Default: 100 MiB — matches AgentCore Runtime's documented payload cap.
     */
    public var maxRequestBytes: Long = 100L * 1024 * 1024

    /**
     * Per-request handler timeout, in milliseconds. When > 0, the unified [handler] / typed
     * handler is wrapped in a `withTimeout(...)` block. On timeout the request fails with
     * **HTTP 504 Gateway Timeout** and a structured JSON error body.
     *
     * Default: 0 — no timeout.
     */
    public var handlerTimeoutMillis: Long = 0

    /** The typed handler registered via [handle], if any. */
    @PublishedApi
    internal var typedInvocation: TypedInvocation? = null

    /** Custom ping service. Defaults to [StaticAgentCorePingService]. */
    public var pingService: AgentCorePingService? = null

    /**
     * Task tracker used to report [PingStatus.HEALTHY_BUSY] while long-running background work
     * is in progress. Exposed to the handler via [AgentCoreContext.taskTracker].
     */
    public var taskTracker: AgentCoreTaskTracker = AgentCoreTaskTracker()
}

/**
 * Registers a typed invocation handler — convenience for JSON-in / JSON-out flows.
 *
 * When set, the unified [AgentCoreRuntimeConfig.handler] is ignored. Requires
 * `ContentNegotiation` (e.g. `json()`) to be installed on the application.
 *
 * Example:
 * ```kotlin
 * routing {
 *     agentCoreRuntime {
 *         handle<MyRequest, MyResponse> { input, ctx ->
 *             MyResponse(answer = process(input.prompt))
 *         }
 *     }
 * }
 * ```
 */
public inline fun <reified I : Any, reified O> AgentCoreRuntimeConfig.handle(
    crossinline handler: TypedInvocationHandler<I, O>,
) {
    typedInvocation = TypedInvocation(typeInfo<I>(), typeInfo<O>()) { input, context ->
        @Suppress("UNCHECKED_CAST")
        handler(input as I, context)
    }
}

// ---------------------------------------------------------------------------
//  Response writers
// ---------------------------------------------------------------------------

/**
 * Resolves the response `Content-Type` for the `/invocations` endpoint from the request's
 * `Accept` header. Used for [InvocationResult.Text]: when the client asked for SSE we frame
 * the single string as one `data:` event; for octet-stream we write raw bytes; for plain text
 * we use `text/plain`; otherwise JSON.
 */
internal fun resolveResponseContentType(acceptHeader: String?): ContentType {
    if (acceptHeader.isNullOrBlank()) return ContentType.Application.Json
    val accepted = parseHeaderValue(acceptHeader).map { it.value }
    return when {
        accepted.any { it.startsWith(ContentType.Text.EventStream.toString()) } -> ContentType.Text.EventStream
        accepted.any { it.startsWith(ContentType.Application.OctetStream.toString()) } -> ContentType.Application.OctetStream
        accepted.any { it.startsWith(ContentType.Application.Json.toString()) } -> ContentType.Application.Json
        accepted.any { it.startsWith(ContentType.Text.Plain.toString()) } -> ContentType.Text.Plain
        else -> ContentType.Application.Json
    }
}

/** Writes a textual one-shot result, choosing the responder by negotiated [contentType]. */
internal suspend fun respondInvocationText(call: ApplicationCall, contentType: ContentType, value: String) {
    when (contentType) {
        ContentType.Text.EventStream -> call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
            writeStringUtf8("data: $value\n\n")
            flush()
        }
        ContentType.Application.OctetStream ->
            call.respondBytes(value.toByteArray(Charsets.UTF_8), ContentType.Application.OctetStream)
        else -> call.respondText(value, contentType)
    }
}

/**
 * Streams the invocation output [chunks] back to the client as a `text/event-stream` response,
 * writing each emitted chunk as its own properly framed SSE `data:` event and flushing.
 */
internal suspend fun respondTextStream(call: ApplicationCall, chunks: Flow<String>) {
    call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
        chunks.collect { chunk ->
            writeStringUtf8("data: $chunk\n\n")
            flush()
        }
    }
}

/** Streams [chunks] as a chunked binary response with the supplied [contentType]. */
internal suspend fun respondBinaryStream(call: ApplicationCall, contentType: ContentType, chunks: Flow<ByteArray>) {
    call.respondBytesWriter(contentType = contentType) {
        chunks.collect { bytes ->
            writeFully(bytes, 0, bytes.size)
            flush()
        }
    }
}

/** Dispatches an [InvocationResult] to the appropriate writer. */
internal suspend fun respondInvocationResult(call: ApplicationCall, accept: ContentType, result: InvocationResult) {
    when (result) {
        is InvocationResult.Text -> respondInvocationText(call, accept, result.value)
        is InvocationResult.Binary -> call.respondBytes(result.bytes, result.contentType)
        is InvocationResult.TextStream -> respondTextStream(call, result.chunks)
        is InvocationResult.BinaryStream -> respondBinaryStream(call, result.contentType, result.chunks)
    }
}

// ---------------------------------------------------------------------------
//  Input dispatcher
// ---------------------------------------------------------------------------

/**
 * Builds a single-key JSON error body using kotlinx-serialization.
 * Optionally includes the throwable simple name as `type`.
 */
internal fun jsonError(message: String, type: String? = null): String =
    kotlinx.serialization.json.Json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        kotlinx.serialization.json.buildJsonObject {
            put("error", kotlinx.serialization.json.JsonPrimitive(message))
            if (type != null) put("type", kotlinx.serialization.json.JsonPrimitive(type))
        },
    )

/**
 * Runs [block] with an optional timeout in milliseconds. When [timeoutMillis] is `<= 0`
 * the block runs without any timeout wrapper.
 */
internal suspend inline fun <T> withOptionalTimeout(timeoutMillis: Long, crossinline block: suspend () -> T): T =
    if (timeoutMillis > 0) kotlinx.coroutines.withTimeout(timeoutMillis) { block() } else block()

/**
 * Reads the request body and turns it into an [InvocationInput] variant according to the
 * configured [thresholdBytes] and the request's `Content-Type` / `Content-Length`.
 */
internal suspend fun readInvocationInput(call: ApplicationCall, thresholdBytes: Long): InvocationInput {
    val ct = call.request.contentType()

    if (ct.match(ContentType.MultiPart.FormData) || ct.contentType.equals("multipart", ignoreCase = true)) {
        return InvocationInput.Multipart(call.receiveMultipart())
    }

    val isText = ct.match(ContentType.Application.Json) ||
        ct.contentType.equals("text", ignoreCase = true)

    if (isText) {
        return InvocationInput.Text(call.receiveText())
    }

    val length = call.request.contentLength() ?: -1L
    return if (length in 0..thresholdBytes) {
        InvocationInput.Binary(call.receive<ByteArray>(), ct)
    } else {
        InvocationInput.Stream(call.receiveChannel(), ct)
    }
}

// ---------------------------------------------------------------------------
//  Route installer
// ---------------------------------------------------------------------------

/**
 * Installs routes implementing the Amazon Bedrock AgentCore Runtime contract:
 *
 * - `GET /ping` — health check with task-aware status reporting.
 * - `POST /invocations` — invocation endpoint backed by a single unified handler.
 *
 * Quick start:
 * ```kotlin
 * routing {
 *     agentCoreRuntime {
 *         handler = { input, ctx ->
 *             when (input) {
 *                 is InvocationInput.Text   -> InvocationResult.Text("ok: ${input.body}")
 *                 is InvocationInput.Binary -> InvocationResult.Binary(input.bytes, input.contentType)
 *                 is InvocationInput.Stream -> InvocationResult.Text("streamed ${input.contentType}")
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * Application plugins such as `ContentNegotiation` and `RateLimit` remain owned by the host
 * application and can be applied to these routes through normal Ktor route composition.
 */
public fun Route.agentCoreRuntime(configure: AgentCoreRuntimeConfig.() -> Unit): Route = route("") {
    val logger = LoggerFactory.getLogger("AgentCoreRuntime")
    val config = AgentCoreRuntimeConfig().apply(configure)
    val taskTracker = config.taskTracker
    val pingService = config.pingService ?: StaticAgentCorePingService(taskTracker)

    val unifiedHandler = config.handler
    val typedInvocation = config.typedInvocation
    val streamThreshold = config.binaryStreamThresholdBytes
    val maxRequestBytes = config.maxRequestBytes
    val handlerTimeoutMillis = config.handlerTimeoutMillis
    if (unifiedHandler == null && typedInvocation == null) {
        throw AgentCoreInvocationException(
            "No invocation handler configured. Set AgentCoreRuntimeConfig.handler or " +
                "register a typed handler via handle<I, O> { ... }."
        )
    }

    get("/ping") {
        val pingResponse = pingService.getPingStatus()
        call.respondText(
            """{"status":"${pingResponse.status}","time_of_last_update":${pingResponse.timeOfLastUpdate}}""",
            ContentType.Application.Json,
            pingResponse.httpStatus,
        )
    }

    post("/invocations") {
        val context = AgentCoreContext(call.request.headers, taskTracker)

        // 413 — Payload Too Large (only when Content-Length is announced)
        val contentLength = call.request.contentLength()
        if (contentLength != null && contentLength > maxRequestBytes) {
            val body = jsonError("Payload too large: $contentLength bytes (max $maxRequestBytes)")
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.PayloadTooLarge)
            return@post
        }

        try {
            if (typedInvocation != null) {
                // Typed JSON-in / JSON-out via ContentNegotiation
                val input = call.receive<Any?>(typedInvocation.inputType)
                val result = withOptionalTimeout(handlerTimeoutMillis) {
                    typedInvocation.handle(this@post, input, context)
                }
                call.respond(result, typedInvocation.outputType)
            } else {
                // Unified handler covers all (Input × Output) shapes
                val input = readInvocationInput(call, streamThreshold)
                val accept = resolveResponseContentType(call.request.header(HttpHeaders.Accept))
                val result = withOptionalTimeout(handlerTimeoutMillis) {
                    unifiedHandler!!(this@post, input, context)
                }
                respondInvocationResult(call, accept, result)
            }
        } catch (t: TimeoutCancellationException) {
            logger.error("Handler timeout (${handlerTimeoutMillis}ms exceeded)", t)
            val body = jsonError("Handler timed out after ${handlerTimeoutMillis}ms")
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.GatewayTimeout)
        } catch (t: Throwable) {
            logger.error("Error invoking AgentCore handler: ${t.message}", t)
            val msg = t.message ?: t::class.simpleName ?: "error"
            val body = jsonError(msg, t::class.simpleName)
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }
}
