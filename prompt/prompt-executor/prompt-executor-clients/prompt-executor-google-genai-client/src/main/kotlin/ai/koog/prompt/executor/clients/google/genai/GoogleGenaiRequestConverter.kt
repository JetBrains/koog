package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import com.google.genai.types.Blob
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.Part

/**
 * Converts a Koog [Prompt] into Google GenAI SDK [Content] objects and system instructions.
 *
 * @property fallbackThoughtSignature Thought signature used for thinking models when no signature is available.
 * @property conversionUtils Shared utilities for JSON parsing and signature encoding.
 */
internal class GoogleGenaiRequestConverter(
    private val fallbackThoughtSignature: String,
    private val conversionUtils: GoogleGenaiConversionUtils
) {

    /**
     * Converts a [Prompt] to SDK [Content] list and optional system instruction.
     *
     * @return Pair of (conversation contents, system instruction content or null)
     */
    fun buildSdkContents(
        prompt: Prompt,
        model: LLModel
    ): Pair<List<Content>, Content?> {
        val systemParts = mutableListOf<Part>()
        val contents = mutableListOf<Content>()
        val pendingCalls = mutableListOf<Part>()
        val pendingResults = mutableListOf<Part>()
        var lastSignature: String? = null
        val isThinkingModel = model.supports(LLMCapability.Thinking)

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                contents += Content.builder().role("model").parts(pendingCalls.toList()).build()
                pendingCalls.clear()
            }
        }

        fun flushResults() {
            if (pendingResults.isNotEmpty()) {
                contents += Content.builder().role("user").parts(pendingResults.toList()).build()
                pendingResults.clear()
            }
        }

        fun flushAll() {
            flushCalls()
            flushResults()
        }

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    systemParts.add(Part.fromText(message.content))
                }

                is Message.User -> {
                    flushAll()
                    contents.add(buildUserContent(message, model))
                }

                is Message.Assistant -> {
                    flushAll()
                    contents.add(buildAssistantContent(message))
                }

                is Message.Reasoning -> {
                    flushAll()

                    if (message.content.isNotBlank()) {
                        val partBuilder = Part.builder().text(message.content).thought(true)
                        message.encrypted?.let {
                            partBuilder.thoughtSignature(conversionUtils.signatureToBytes(it))
                        }
                        contents.add(
                            Content.builder().role("model").parts(listOf(partBuilder.build())).build()
                        )
                    }
                    // Always propagate the signature so subsequent Tool.Call parts can use it
                    lastSignature = message.encrypted
                }

                is Message.Tool.Result -> {
                    pendingResults.add(
                        Part.fromFunctionResponse(
                            message.tool,
                            mapOf("result" to message.content)
                        )
                    )
                }

                is Message.Tool.Call -> {
                    val isFirstCallInBatch = pendingCalls.isEmpty()
                    if (isFirstCallInBatch) {
                        flushResults()
                    }

                    val signature = lastSignature
                    lastSignature = null

                    // Only the first functionCall part in a parallel batch needs the signature.
                    // Subsequent parts must not have one (per Google API spec).
                    // See https://docs.cloud.google.com/vertex-ai/generative-ai/docs/thought-signatures
                    val effectiveSignature = if (isFirstCallInBatch) {
                        signature ?: if (isThinkingModel) fallbackThoughtSignature else null
                    } else {
                        signature
                    }

                    val args = conversionUtils.parseJsonToMap(message.content)
                    val partBuilder = Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .name(message.tool)
                                .args(args)
                                .build()
                        )
                    effectiveSignature?.let {
                        partBuilder.thoughtSignature(conversionUtils.signatureToBytes(it))
                    }
                    pendingCalls += partBuilder.build()
                }
            }
        }
        flushAll()

        val systemInstruction = systemParts
            .takeIf { it.isNotEmpty() }
            ?.let { Content.builder().parts(it).build() }

        return contents to systemInstruction
    }

    private fun buildAssistantContent(message: Message.Assistant): Content {
        return Content.builder().role("model").parts(Part.fromText(message.content)).build()
    }

    private fun buildUserContent(message: Message.User, model: LLModel): Content {
        val parts = message.parts.map { part ->
            when (part) {
                is ContentPart.Text -> Part.fromText(part.text)

                is ContentPart.Image -> {
                    require(model.supports(LLMCapability.Vision.Image)) {
                        "Model ${model.id} does not support images"
                    }
                    blobPart(part.content, part.mimeType)
                }

                is ContentPart.Audio -> {
                    require(model.supports(LLMCapability.Audio)) {
                        "Model ${model.id} does not support audio"
                    }
                    blobPart(part.content, part.mimeType)
                }

                is ContentPart.File -> {
                    require(model.supports(LLMCapability.Document)) {
                        "Model ${model.id} does not support documents"
                    }
                    blobPart(part.content, part.mimeType)
                }

                is ContentPart.Video -> {
                    require(model.supports(LLMCapability.Vision.Video)) {
                        "Model ${model.id} does not support video"
                    }
                    blobPart(part.content, part.mimeType)
                }
            }
        }
        return Content.builder().role("user").parts(parts).build()
    }

    private fun blobPart(content: AttachmentContent, mimeType: String): Part {
        val bytes = when (content) {
            is AttachmentContent.Binary -> content.asBytes()
            else -> throw IllegalArgumentException("Unsupported attachment content: ${content::class}")
        }
        return Part.builder().inlineData(
            Blob.builder().data(bytes).mimeType(mimeType).build()
        ).build()
    }
}
