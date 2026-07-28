package com.example.koogagentcore

import ai.koog.agentcore.runtime.InvocationInput
import ai.koog.agentcore.runtime.InvocationResult
import ai.koog.agentcore.runtime.streamingBytesFlow
import io.ktor.http.ContentType
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * E2E coverage harness used by `deploy/test.sh` to verify every
 * `InvocationInput × InvocationResult` cell against a real AgentCore Runtime
 * deployment. Not part of the primary chat agent; lives here so the deployment
 * can serve both the documented use case and full plugin regression coverage
 * from a single container.
 *
 * Routing:
 * - `JSON { action: "..." }` requests are dispatched in [handleAction].
 * - Raw binary requests (`Content-Type: image/png` etc.) come through [handleBinary].
 * - Large requests above `binaryStreamThresholdBytes` come through [handleStream].
 * - `multipart/form-data` requests come through [handleMultipart].
 */
internal object E2eHarness {

    @Serializable
    private data class BinaryAck(val variant: String, val contentType: String, val size: Int)

    @Serializable
    private data class MultipartAck(val variant: String, val parts: List<String>)

    @Serializable
    private data class ImageOutRequest(val action: String, val imageBase64: String, val imageFormat: String? = null)

    @Serializable
    private data class BytesStreamRequest(val action: String, val sizeBytes: Int = 1024, val chunkBytes: Int = 64)

    suspend fun handleAction(body: String, action: String): InvocationResult = when (action) {
        // ──── Text-shaped one-shot output ─────────────────────────────
        "string-out" -> InvocationResult.Text("plain text response")
        "json-out"   -> InvocationResult.Text("""{"greeting":"hello","echo":"$action"}""")

        // ──── Binary one-shot output (InvocationResult.Binary) ────────
        "image-out" -> {
            val req = json.decodeFromString(ImageOutRequest.serializer(), body)
            val bytes = Base64.getDecoder().decode(req.imageBase64)
            val ct = ContentType("image", req.imageFormat ?: "png")
            InvocationResult.Binary(bytes, ct)
        }

        // ──── Streaming binary output (InvocationResult.BinaryStream) ─
        "bytes-stream" -> {
            val req = json.decodeFromString(BytesStreamRequest.serializer(), body)
            val total = req.sizeBytes.coerceIn(1, 256 * 1024)
            val chunk = req.chunkBytes.coerceIn(1, 4096)
            InvocationResult.BinaryStream(
                streamingBytesFlow {
                    var sent = 0
                    while (sent < total) {
                        val n = minOf(chunk, total - sent)
                        // simple deterministic content: repeating 'A'..'Z'
                        val buf = ByteArray(n) { i -> ('A'.code + (sent + i) % 26).toByte() }
                        send(buf)
                        sent += n
                    }
                },
                ContentType.Application.OctetStream,
            )
        }

        // ──── Streaming text output (InvocationResult.TextStream) ─────
        // (Already exercised by primary chat; included for completeness.)
        "text-stream" -> InvocationResult.TextStream(
            ai.koog.agentcore.runtime.streamingFlow {
                listOf("first ", "second ", "third").forEach { send(it) }
            }
        )

        else -> error("unknown action: '$action'. Supported: " +
            "string-out, json-out, image-out, bytes-stream, text-stream")
    }

    fun handleBinary(input: InvocationInput.Binary): InvocationResult {
        val ack = BinaryAck("binary-in", input.contentType.toString(), input.bytes.size)
        return InvocationResult.Text(json.encodeToString(BinaryAck.serializer(), ack))
    }

    suspend fun handleStream(input: InvocationInput.Stream): InvocationResult {
        // Read the channel fully — for demo. Real apps would process incrementally.
        val read = input.channel.toByteArray()
        val ack = BinaryAck("stream-in", input.contentType.toString(), read.size)
        return InvocationResult.Text(json.encodeToString(BinaryAck.serializer(), ack))
    }

    suspend fun handleMultipart(input: InvocationInput.Multipart): InvocationResult {
        val parts = mutableListOf<String>()
        var part = input.parts.readPart()
        while (part != null) {
            parts.add("${part::class.simpleName}:${part.name}")
            part.dispose()
            part = input.parts.readPart()
        }
        val ack = MultipartAck("multipart-in", parts)
        return InvocationResult.Text(json.encodeToString(MultipartAck.serializer(), ack))
    }
}
