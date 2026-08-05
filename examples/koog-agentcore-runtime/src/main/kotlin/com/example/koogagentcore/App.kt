package com.example.koogagentcore

import ai.koog.agentcore.runtime.InvocationInput
import ai.koog.agentcore.runtime.InvocationResult
import ai.koog.agentcore.runtime.agentCoreRuntime
import ai.koog.agentcore.runtime.streamingFlow
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ConversationRole
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseOutput
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamOutput
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ImageBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ImageFormat
import aws.sdk.kotlin.services.bedrockruntime.model.ImageSource
import aws.sdk.kotlin.services.bedrockruntime.model.Message
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Minimal vision-enabled streaming chat agent for Amazon Bedrock AgentCore Runtime.
 *
 * **Primary use case** — what a real agent looks like. The same agent serves both a
 * streaming and a non-streaming response; the client picks per request via the `Accept`
 * header — no separate deployment or mode is required:
 * ```
 *   # Streaming — Accept: text/event-stream
 *   POST /invocations
 *   Content-Type: application/json
 *   Accept:       text/event-stream
 *
 *   { "prompt": "...", "imageBase64": "<optional>", "imageFormat": "png|jpeg|gif|webp" }
 *
 *   ← data: <token-1>\n\n
 *   ← data: <token-2>\n\n
 *
 *   # Non-streaming — any other Accept (e.g. application/json, text/plain)
 *   POST /invocations
 *   Content-Type: application/json
 *   Accept:       text/plain
 *
 *   { "prompt": "..." }
 *
 *   ← <full answer as a single body>
 * ```
 *
 * The handler also delegates to [E2eHarness] for non-primary request shapes (binary input,
 * multipart upload, action-based test envelopes). This keeps the deployed runtime usable
 * as a regression harness for the plugin's full input/output matrix without bloating the
 * primary agent code path.
 */

internal const val REGION = "us-east-1"
internal const val MODEL = "global.anthropic.claude-opus-4-6-v1"

@Serializable
internal data class ChatRequest(
    val prompt: String,
    val imageBase64: String? = null,
    val imageFormat: String? = null,
)

internal val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(CallLogging)
    routing {
        agentCoreRuntime {
            // Lower than default so the e2e tests can exercise the Stream variant without
            // needing a multi-MB request.
            binaryStreamThresholdBytes = 64 * 1024
            handlerTimeoutMillis = 60_000
            handler = { input, ctx ->
                when (input) {
                    is InvocationInput.Text -> handleText(input.body, ctx.getHeader("Accept"))
                    is InvocationInput.Binary -> handleBinary(input)
                    is InvocationInput.Stream -> handleStream(input)
                    is InvocationInput.Multipart -> handleMultipart(input)
                }
            }
        }
    }
}

/**
 * Text-shaped (JSON / text/...) request. If it carries an `action` field it's a
 * deployment-coverage test — delegate to [E2eHarness]. Otherwise it's the primary
 * chat path, which streams or returns a one-shot answer based on the `Accept` header.
 *
 * `Accept: text/event-stream` → streamed SSE ([InvocationResult.TextStream]); any other
 * `Accept` → the full answer as a single body ([InvocationResult.Text]). Both are served
 * by the same handler and the same deployment.
 */
private suspend fun handleText(body: String, accept: String?): InvocationResult {
    val obj = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
    val action = obj?.get("action")?.jsonPrimitive?.content
    return if (action != null) {
        E2eHarness.handleAction(body, action)
    } else {
        val req = json.decodeFromString(ChatRequest.serializer(), body)
        if (accept?.contains("text/event-stream") == true) {
            InvocationResult.TextStream(streamChat(req))
        } else {
            InvocationResult.Text(chat(req))
        }
    }
}

private fun handleBinary(input: InvocationInput.Binary): InvocationResult =
    E2eHarness.handleBinary(input)

private suspend fun handleStream(input: InvocationInput.Stream): InvocationResult =
    E2eHarness.handleStream(input)

private suspend fun handleMultipart(input: InvocationInput.Multipart): InvocationResult =
    E2eHarness.handleMultipart(input)

/**
 * Non-streaming chat: calls Bedrock's `converse` (a single request/response, no streaming)
 * and returns the full answer as one string. This is the genuine non-streaming counterpart
 * to [streamChat] — a distinct Bedrock API, not a buffered stream.
 */
internal suspend fun chat(req: ChatRequest): String =
    BedrockRuntimeClient { region = REGION }.use { client ->
        val resp = client.converse(
            ConverseRequest {
                modelId = MODEL
                messages = listOf(buildUserMessage(req))
                inferenceConfig {
                    maxTokens = 600
                    temperature = 0.4F
                }
            }
        )
        (resp.output as? ConverseOutput.Message)?.value?.content
            ?.mapNotNull { (it as? ContentBlock.Text)?.value }
            ?.joinToString("")
            .orEmpty()
    }

/**
 * Streams Bedrock Converse token deltas. Uses `streamingFlow` (channelFlow under the hood)
 * because the AWS SDK's `converseStream` switches coroutine contexts internally — a plain
 * `flow { emit() }` would throw `IllegalStateException: Flow invariant is violated`.
 */
internal fun streamChat(req: ChatRequest) = streamingFlow {
    BedrockRuntimeClient { region = REGION }.use { client ->
        client.converseStream(
            ConverseStreamRequest {
                modelId = MODEL
                messages = listOf(buildUserMessage(req))
                inferenceConfig {
                    maxTokens = 600
                    temperature = 0.4F
                }
            }
        ) { resp ->
            resp.stream?.collect { chunk ->
                if (chunk is ConverseStreamOutput.ContentBlockDelta) {
                    chunk.value.delta?.asTextOrNull()?.let { send(it) }
                }
            }
        }
    }
}

private fun buildUserMessage(req: ChatRequest): Message = Message {
    role = ConversationRole.User
    content = buildList {
        add(ContentBlock.Text(req.prompt))
        if (req.imageBase64 != null) {
            add(
                ContentBlock.Image(
                    ImageBlock {
                        format = imageFormatOf(req.imageFormat ?: "png")
                        source = ImageSource.Bytes(Base64.getDecoder().decode(req.imageBase64))
                    }
                )
            )
        }
    }
}

internal fun imageFormatOf(s: String): ImageFormat = when (s.lowercase()) {
    "jpeg", "jpg" -> ImageFormat.Jpeg
    "gif" -> ImageFormat.Gif
    "webp" -> ImageFormat.Webp
    else -> ImageFormat.Png
}
