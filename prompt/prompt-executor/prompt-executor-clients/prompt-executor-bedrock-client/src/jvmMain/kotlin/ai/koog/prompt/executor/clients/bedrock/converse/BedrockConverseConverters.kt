package ai.koog.prompt.executor.clients.bedrock.converse

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockToolSerialization
import ai.koog.prompt.executor.clients.bedrock.util.JsonDocumentConverters
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.buildStreamFrameFlow
import aws.sdk.kotlin.services.bedrockruntime.model.AnyToolChoice
import aws.sdk.kotlin.services.bedrockruntime.model.AutoToolChoice
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlockDelta
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlockStart
import aws.sdk.kotlin.services.bedrockruntime.model.ConversationRole
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamOutput
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.DocumentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.DocumentFormat
import aws.sdk.kotlin.services.bedrockruntime.model.DocumentSource
import aws.sdk.kotlin.services.bedrockruntime.model.ImageBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ImageFormat
import aws.sdk.kotlin.services.bedrockruntime.model.ImageSource
import aws.sdk.kotlin.services.bedrockruntime.model.InferenceConfiguration
import aws.sdk.kotlin.services.bedrockruntime.model.PerformanceConfiguration
import aws.sdk.kotlin.services.bedrockruntime.model.PromptVariableValues
import aws.sdk.kotlin.services.bedrockruntime.model.ReasoningContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ReasoningTextBlock
import aws.sdk.kotlin.services.bedrockruntime.model.S3Location
import aws.sdk.kotlin.services.bedrockruntime.model.SpecificToolChoice
import aws.sdk.kotlin.services.bedrockruntime.model.SystemContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ToolConfiguration
import aws.sdk.kotlin.services.bedrockruntime.model.ToolInputSchema
import aws.sdk.kotlin.services.bedrockruntime.model.ToolResultBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ToolResultContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ToolSpecification
import aws.sdk.kotlin.services.bedrockruntime.model.ToolUseBlock
import aws.sdk.kotlin.services.bedrockruntime.model.VideoBlock
import aws.sdk.kotlin.services.bedrockruntime.model.VideoFormat
import aws.sdk.kotlin.services.bedrockruntime.model.VideoSource
import aws.smithy.kotlin.runtime.content.Document
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import aws.sdk.kotlin.services.bedrockruntime.model.Message as BedrockMessage
import aws.sdk.kotlin.services.bedrockruntime.model.Tool as BedrockTool
import aws.sdk.kotlin.services.bedrockruntime.model.ToolChoice as BedrockToolChoice

internal object BedrockConverseConverters {
    private val logger = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Even though [ConverseRequest] and [ConverseStreamRequest] are structurally identical, they don't share a common
     * parent class. This class extracts common request parameters to avoid excessive code duplication.
     */
    private class ConverseRequestParams(
        val modelId: String,
        val inferenceConfig: InferenceConfiguration,
        val additionalModelRequestFields: Document?,
        val performanceConfig: PerformanceConfiguration?,
        val promptVariables: Map<String, PromptVariableValues>?,
        val requestMetadata: Map<String, String>?,
        val toolConfig: ToolConfiguration?,
        val system: List<SystemContentBlock>,
        val messages: List<BedrockMessage>,
    )

    /**
     * Creates a common set of Converse API requests parameters.
     */
    private fun createConverseRequestParams(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): ConverseRequestParams {
        val params = prompt.params.toBedrockConverseParams()

        val systemMessages = mutableListOf<SystemContentBlock>()
        val messages = mutableListOf<BedrockMessage>()

        // Convert Prompt messages to bedrock message formats
        prompt.messages.forEach { message ->
            when (message) {
                is Message.System ->
                    systemMessages += message.parts.map { SystemContentBlock.Text(it.text) }

                is Message.User ->
                    messages += BedrockMessage {
                        this.role = ConversationRole.User
                        this.content = message.parts.map { it.toConverseContentBlock(model) }
                    }

                is Message.Assistant ->
                    messages += BedrockMessage {
                        this.role = ConversationRole.Assistant
                        this.content = message.parts.map { it.toConverseContentBlock(model) }
                    }

                is Message.Reasoning ->
                    messages += BedrockMessage {
                        this.role = ConversationRole.Assistant

                        this.content = listOf(
                            ContentBlock.ReasoningContent(
                                ReasoningContentBlock.ReasoningText(
                                    ReasoningTextBlock {
                                        this.text = message.content
                                        this.signature = message.encrypted
                                    }
                                )
                            )
                        )
                    }

                is Message.Tool.Call ->
                    messages += BedrockMessage {
                        this.role = ConversationRole.Assistant

                        this.content = listOf(
                            ContentBlock.ToolUse(
                                ToolUseBlock {
                                    this.name = message.tool
                                    this.toolUseId = message.id
                                    this.input = JsonDocumentConverters.convertToDocument(message.contentJson)
                                }
                            )
                        )
                    }

                is Message.Tool.Result ->
                    messages += BedrockMessage {
                        this.role = ConversationRole.User

                        this.content = listOf(
                            ContentBlock.ToolResult(
                                ToolResultBlock {
                                    this.toolUseId = message.id
                                    // only text results are currently supported
                                    this.content = message.parts.map { ToolResultContentBlock.Text(it.text) }
                                }
                            )
                        )
                    }
            }
        }

        return ConverseRequestParams(
            modelId = model.id,
            inferenceConfig = InferenceConfiguration {
                this.maxTokens = params.maxTokens
                this.temperature = params.temperature
                    ?.takeIf { model.supports(LLMCapability.Temperature) }
                    ?.toFloat()
            },
            additionalModelRequestFields = params.additionalProperties
                ?.let { JsonDocumentConverters.convertToDocument(JsonObject(it)) },
            performanceConfig = params.performanceConfig,
            promptVariables = params.promptVariables,
            requestMetadata = params.requestMetadata,
            toolConfig = if (tools.isNotEmpty()) {
                ToolConfiguration {
                    this.toolChoice = when (val toolChoice = params.toolChoice) {
                        is LLMParams.ToolChoice.Named ->
                            BedrockToolChoice.Tool(
                                SpecificToolChoice {
                                    this.name = toolChoice.name
                                }
                            )

                        is LLMParams.ToolChoice.None ->
                            throw IllegalArgumentException("Bedrock Converse API doesn't support 'none' tool choice.")

                        is LLMParams.ToolChoice.Auto ->
                            BedrockToolChoice.Auto(
                                AutoToolChoice {
                                    // no params in SDK
                                }
                            )

                        is LLMParams.ToolChoice.Required ->
                            BedrockToolChoice.Any(
                                AnyToolChoice {
                                    // no params in SDK
                                }
                            )

                        null -> null
                    }

                    this.tools = tools.map { it.toConverseTool() }
                }
            } else {
                null
            },
            system = systemMessages,
            messages = messages,
        )
    }

    /**
     * Creates regular [ConverseRequest].
     */
    fun createConverseRequest(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): ConverseRequest {
        val params = createConverseRequestParams(prompt, model, tools)

        @Suppress("DuplicatedCode") // AWS SDK requires duplication
        return ConverseRequest {
            this.modelId = params.modelId
            this.inferenceConfig = params.inferenceConfig
            this.additionalModelRequestFields = params.additionalModelRequestFields
            this.performanceConfig = params.performanceConfig
            this.promptVariables = params.promptVariables
            this.toolConfig = params.toolConfig
            this.system = params.system
            this.messages = params.messages
        }
    }

    /**
     * Creates [ConverseStreamRequest] for streaming response.
     */
    fun createConverseStreamRequest(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): ConverseStreamRequest {
        val params = createConverseRequestParams(prompt, model, tools)

        @Suppress("DuplicatedCode") // AWS SDK requires duplication
        return ConverseStreamRequest {
            this.modelId = params.modelId
            this.inferenceConfig = params.inferenceConfig
            this.additionalModelRequestFields = params.additionalModelRequestFields
            this.performanceConfig = params.performanceConfig
            this.promptVariables = params.promptVariables
            this.toolConfig = params.toolConfig
            this.system = params.system
            this.messages = params.messages
        }
    }

    /**
     * Converts [ConverseRequest] response.
     */
    fun convertConverseResponse(
        response: ConverseResponse,
        clock: Clock,
    ): List<Message.Response> {
        // Extract token count from the response
        val inputTokensCount = response.usage?.inputTokens
        val outputTokensCount = response.usage?.outputTokens
        val totalTokensCount = response.usage?.totalTokens
        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = totalTokensCount,
            inputTokensCount = inputTokensCount,
            outputTokensCount = outputTokensCount,
        )

        val content = response.output?.asMessageOrNull()?.content.orEmpty()
        // Convert content blocks to messages
        val messages = content.map { contentBlock ->
            when (contentBlock) {
                is ContentBlock.ReasoningContent -> when (val reasoningContent = contentBlock.value) {
                    is ReasoningContentBlock.ReasoningText -> {
                        val reasoningBlock = reasoningContent.value

                        Message.Reasoning(
                            encrypted = reasoningBlock.signature,
                            content = reasoningBlock.text,
                            metaInfo = metaInfo,
                        )
                    }

                    else ->
                        throw IllegalArgumentException("Unsupported reasoning content type from Bedrock Converse API: $reasoningContent")
                }

                is ContentBlock.ToolUse -> {
                    val toolUseBlock = contentBlock.value

                    Message.Tool.Call(
                        id = toolUseBlock.toolUseId,
                        tool = toolUseBlock.name,
                        content = json.encodeToString(
                            JsonDocumentConverters.convertToJsonElement(toolUseBlock.input)
                        ),
                        metaInfo = metaInfo,
                    )
                }

                else ->
                    Message.Assistant(
                        part = contentBlock.toContentPart(),
                        metaInfo = metaInfo,
                        finishReason = response.stopReason.value,
                    )
            }
        }

        return messages.ifEmpty {
            // If no messages where returned, return an empty message and check stopReason
            listOf(
                Message.Assistant(
                    content = "",
                    finishReason = response.stopReason.value,
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = totalTokensCount,
                        inputTokensCount = inputTokensCount,
                        outputTokensCount = outputTokensCount,
                    )
                )
            )
        }
    }

    /**
     * Transforms [ConverseStreamRequest] response stream.
     */
    fun transformConverseStreamChunks(
        chunkFlow: Flow<ConverseStreamOutput>,
        clock: Clock = Clock.System,
    ) = buildStreamFrameFlow {
        var finishReason: String? = null

        chunkFlow.collect { chunk ->
            when (chunk) {
                is ConverseStreamOutput.MessageStart -> {
                    logger.debug { "Received start message from Converse" }
                }

                is ConverseStreamOutput.ContentBlockStart -> when (val start = chunk.value.start) {
                    is ContentBlockStart.ToolUse -> {
                        upsertToolCall(
                            index = chunk.value.contentBlockIndex,
                            id = start.value.toolUseId,
                            name = start.value.name,
                        )
                    }

                    null -> {
                        // skip
                    }

                    is ContentBlockStart.Image, is ContentBlockStart.ToolResult -> {
                        logger.warn { "Unsupported Converse content block start type: ${start::class.simpleName}" }
                    }

                    ContentBlockStart.SdkUnknown -> {
                        logger.warn { "Unknown Converse content block start type: ${start::class.simpleName}" }
                    }
                }

                is ConverseStreamOutput.ContentBlockDelta -> when (val delta = chunk.value.delta) {
                    is ContentBlockDelta.Text -> {
                        emitAppend(delta.value)
                    }

                    is ContentBlockDelta.ToolUse -> {
                        upsertToolCall(
                            index = chunk.value.contentBlockIndex,
                            args = delta.value.input
                        )
                    }

                    is ContentBlockDelta.Citation, is ContentBlockDelta.ReasoningContent -> {
                        logger.warn { "Unsupported Converse content block delta type: ${delta::class.simpleName}" }
                    }

                    is ContentBlockDelta.Image, is ContentBlockDelta.ToolResult -> {
                        logger.warn { "Unsupported Converse content block delta type: ${delta::class.simpleName}" }
                    }

                    null -> {
                        logger.warn { "null content block delta in Converse chunk" }
                    }

                    ContentBlockDelta.SdkUnknown -> {
                        logger.warn { "Unknown Converse content block delta type: ${delta::class.simpleName}" }
                    }
                }

                is ConverseStreamOutput.ContentBlockStop -> {
                    logger.debug { "Received content block stop from Converse" }
                }

                is ConverseStreamOutput.MessageStop -> {
                    finishReason = chunk.value.stopReason.value
                }

                is ConverseStreamOutput.Metadata -> {
                    val usage = chunk.value.usage

                    emitEnd(
                        finishReason = finishReason,
                        metaInfo = ResponseMetaInfo.create(
                            clock = clock,
                            totalTokensCount = usage?.totalTokens,
                            inputTokensCount = usage?.inputTokens,
                            outputTokensCount = usage?.outputTokens,
                        )
                    )
                }

                ConverseStreamOutput.SdkUnknown -> {
                    logger.warn { "Unknown Converse chunk type: ${chunk::class.simpleName}" }
                }
            }
        }
    }

    /**
     * Converts a [ContentPart] to [ContentBlock] for Bedrock Converse API.
     * Some [ContentPart] might be not supported.
     *
     * @throws IllegalArgumentException if the given [ContentPart] is not supported.
     */
    private fun ContentPart.toConverseContentBlock(model: LLModel): ContentBlock {
        return when (val part = this) {
            is ContentPart.Text ->
                ContentBlock.Text(text)

            is ContentPart.Audio ->
                throw IllegalArgumentException("Bedrock Converse API doesn't support audio content.")

            is ContentPart.File -> {
                require(LLMCapability.Document in model.capabilities) {
                    "${model.id} doesn't support documents"
                }

                ContentBlock.Document(
                    DocumentBlock {
                        this.format = DocumentFormat.fromValue(part.format)
                        // Converse API requires no extension in file names
                        this.name = part.fileName?.substringBefore('.')

                        this.source = when (val content = part.content) {
                            is AttachmentContent.Binary.Base64, is AttachmentContent.Binary.Bytes ->
                                DocumentSource.Bytes(content.asBytes())

                            is AttachmentContent.URL ->
                                DocumentSource.S3Location(content.toS3Location())

                            is AttachmentContent.PlainText ->
                                // Even though DocumentSource.Text exists, Converse API requires bytes or s3 uri here
                                DocumentSource.Bytes(content.text.encodeToByteArray())
                        }
                    }
                )
            }

            is ContentPart.Image -> {
                require(LLMCapability.Vision.Image in model.capabilities) {
                    "${model.id} doesn't support images"
                }

                ContentBlock.Image(
                    ImageBlock {
                        this.format = ImageFormat.fromValue(part.format)

                        this.source = when (val content = part.content) {
                            is AttachmentContent.Binary.Base64, is AttachmentContent.Binary.Bytes ->
                                ImageSource.Bytes(content.asBytes())

                            is AttachmentContent.URL ->
                                ImageSource.S3Location(content.toS3Location())

                            is AttachmentContent.PlainText ->
                                throw IllegalArgumentException("Image can't have plain text content")
                        }
                    }
                )
            }

            is ContentPart.Video -> {
                require(LLMCapability.Vision.Video in model.capabilities) {
                    "${model.id} doesn't support videos"
                }

                ContentBlock.Video(
                    VideoBlock {
                        this.format = VideoFormat.fromValue(part.format)

                        this.source = when (val content = part.content) {
                            is AttachmentContent.Binary.Base64, is AttachmentContent.Binary.Bytes ->
                                VideoSource.Bytes(content.asBytes())

                            is AttachmentContent.URL ->
                                VideoSource.S3Location(content.toS3Location())

                            is AttachmentContent.PlainText ->
                                throw IllegalArgumentException("Video can't have plain text content")
                        }
                    }
                )
            }
        }
    }

    /**
     * Converts a [ContentBlock] from Bedrock Converse API to [ContentPart].
     * Some [ContentBlock] might be not supported.
     * Some [ContentBlock] correspond to [Message], e.g. tool calls and reasoning, so these are handled separately
     * in the message conversion logic.
     *
     * @throws IllegalArgumentException if the given [ContentBlock] is not supported.
     */
    private fun ContentBlock.toContentPart(): ContentPart {
        return when (val block = this) {
            is ContentBlock.Text ->
                ContentPart.Text(block.value)

            is ContentBlock.Document -> {
                val content = when (val source = block.value.source) {
                    is DocumentSource.Bytes ->
                        AttachmentContent.Binary.Bytes(source.value)

                    is DocumentSource.S3Location ->
                        AttachmentContent.URL(source.value.uri)

                    is DocumentSource.Text ->
                        AttachmentContent.PlainText(source.value)

                    else ->
                        throw IllegalArgumentException("Unsupported document source type from Bedrock Converse API: $source")
                }

                val format = block.value.format.value

                ContentPart.File(
                    content = content,
                    fileName = "${block.value.name}.$format",
                    format = format,
                    mimeType = "" // Bedrock Converse API doesn't have mime type
                )
            }

            is ContentBlock.Image -> {
                val content = when (val source = block.value.source) {
                    is ImageSource.Bytes ->
                        AttachmentContent.Binary.Bytes(source.value)

                    is ImageSource.S3Location ->
                        AttachmentContent.URL(source.value.uri)

                    else ->
                        throw IllegalArgumentException("Unsupported image source type from Bedrock Converse API: $source")
                }

                ContentPart.Image(
                    content = content,
                    format = block.value.format.value,
                    fileName = "", // Bedrock Converse API doesn't have file name for images
                    mimeType = "" // Bedrock Converse API doesn't have mime type
                )
            }

            is ContentBlock.Video -> {
                val content = when (val source = block.value.source) {
                    is VideoSource.Bytes ->
                        AttachmentContent.Binary.Bytes(source.value)

                    is VideoSource.S3Location ->
                        AttachmentContent.URL(source.value.uri)

                    else ->
                        throw IllegalArgumentException("Unsupported video source type from Bedrock Converse API: $source")
                }

                ContentPart.Video(
                    content = content,
                    format = block.value.format.value,
                    fileName = "", // Bedrock Converse API doesn't have file name for videos
                    mimeType = "" // Bedrock Converse API doesn't have mime type
                )
            }

            else ->
                throw IllegalArgumentException("Unsupported content block type from Bedrock Converse API: $block")
        }
    }

    /**
     * Helper function to convert URLs in attachment contents to S3 locations.
     * Performs a check if URL is indeed a valid S3 uri.
     */
    private fun AttachmentContent.URL.toS3Location(): S3Location {
        require(url.startsWith("s3://")) {
            "Only S3 locations are supported when URL attachment content is used with Bedrock Converse API."
        }

        return S3Location {
            this.uri = url
        }
    }

    /**
     * Convert [ToolDescriptor] to [BedrockTool] format.
     */
    private fun ToolDescriptor.toConverseTool(): BedrockTool {
        val tool = this

        return BedrockTool.ToolSpec(
            ToolSpecification {
                val inputSchema = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            (tool.requiredParameters + tool.optionalParameters).forEach { param ->
                                put(
                                    param.name,
                                    BedrockToolSerialization.buildToolParameterSchema(param)
                                )
                            }
                        }
                    )
                    put(
                        "required",
                        buildJsonArray {
                            tool.requiredParameters.forEach { param ->
                                add(param.name)
                            }
                        }
                    )
                }

                this.name = tool.name
                this.description = tool.description
                this.inputSchema = ToolInputSchema.Json(JsonDocumentConverters.convertToDocument(inputSchema))
            }
        )
    }
}
