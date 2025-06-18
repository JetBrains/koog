package ai.koog.prompt.executor.clients.bedrock

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MediaContent
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ResponseStream
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal actual class StaticCredentialsProvider actual constructor(
    private val awsAccessKeyId: String,
    private val awsSecretAccessKey: String
) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials {
        return Credentials(accessKeyId = awsAccessKeyId, secretAccessKey = awsSecretAccessKey)
    }
}

/**
 * Creates a new Bedrock LLM client configured with the specified AWS credentials and settings.
 *
 * @param awsAccessKeyId The AWS access key ID for authentication
 * @param awsSecretAccessKey The AWS secret access key for authentication
 * @param settings Configuration settings for the Bedrock client, such as region and endpoint
 * @param clock A clock used for time-based operations
 * @return A configured [LLMClient] instance for Bedrock
 */
public actual fun createBedrockLLMClient(
    awsAccessKeyId: String,
    awsSecretAccessKey: String,
    settings: BedrockClientSettings,
    clock: Clock
): LLMClient = BedrockLLMClientImpl(awsAccessKeyId, awsSecretAccessKey, settings, clock)

private class BedrockLLMClientImpl(
    awsAccessKeyId: String,
    awsSecretAccessKey: String,
    private val settings: BedrockClientSettings,
    private val clock: Clock
) : LLMClient {

    private val logger = KotlinLogging.logger {}

    private val bedrockClient = BedrockRuntimeClient {
        this.region = settings.region
        this.credentialsProvider = StaticCredentialsProvider(awsAccessKeyId, awsSecretAccessKey)

        // Configure a custom endpoint if provided
        settings.endpointUrl?.let { url ->
            this.endpointUrl = Url.parse(url)
        }

        // Configure retry policy
        this.retryStrategy = StandardRetryStrategy {
            maxAttempts = settings.maxRetries
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        logger.debug { "Executing prompt for model: ${model.id} (JVM)" }
        require(model.provider == LLMProvider.Bedrock) { "Model ${model.id} is not a Bedrock model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        // Check tool support
        if (tools.isNotEmpty() && !model.capabilities.contains(LLMCapability.Tools)) {
            throw IllegalArgumentException("Model ${model.id} does not support tools")
        }

        val requestBody = when {
            model.id.startsWith("anthropic.claude") -> createAnthropicRequest(prompt, model, tools)
            model.id.startsWith("amazon.titan") -> createTitanRequest(prompt, model)
            model.id.startsWith("ai21.j2") -> createJurassicRequest(prompt, model)
            model.id.startsWith("cohere.command") -> createCohereRequest(prompt, model)
            model.id.startsWith("meta.llama") -> createLlamaRequest(prompt, model)
            else -> throw IllegalArgumentException("Unsupported Bedrock model: ${model.id}")
        }

        val invokeRequest = InvokeModelRequest {
            this.modelId = model.id
            this.contentType = "application/json"
            this.accept = "*/*"
            this.body = requestBody.toString().encodeToByteArray()
        }

        logger.debug { "Bedrock InvokeModel Request (JVM): ModelID: ${model.id}, Body: $requestBody" }

        try {
            return withContext(Dispatchers.SuitableForIO) {
                val response = bedrockClient.invokeModel(invokeRequest)
                val responseBodyString = response.body?.decodeToString()
                logger.debug { "Bedrock InvokeModel Response (JVM): $responseBodyString" }

                if (responseBodyString.isNullOrBlank()) {
                    logger.error { "Received null or empty body from Bedrock model ${model.id} (JVM)" }
                    return@withContext listOf(
                        Message.Assistant(
                            "Error: Received empty response from Bedrock",
                            metaInfo = ResponseMetaInfo.create(clock)
                        )
                    )
                }

                return@withContext when {
                    model.id.startsWith("anthropic.claude") -> parseAnthropicResponse(responseBodyString, model)
                    model.id.startsWith("amazon.titan") -> parseTitanResponse(responseBodyString)
                    model.id.startsWith("ai21.j2") -> parseJurassicResponse(responseBodyString)
                    model.id.startsWith("cohere.command") -> parseCohereResponse(responseBodyString)
                    model.id.startsWith("meta.llama") -> parseLlamaResponse(responseBodyString)
                    else -> throw IllegalArgumentException("Unsupported Bedrock model: ${model.id}")
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error executing Bedrock prompt for model ${model.id} (JVM)" }
            return listOf(Message.Assistant("Error: ${e.message}", metaInfo = ResponseMetaInfo.create(clock)))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt for model: ${model.id} (JVM)" }
        require(model.provider == LLMProvider.Bedrock) { "Model ${model.id} is not a Bedrock model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val requestBody = when {
            model.id.startsWith("anthropic.claude") -> createAnthropicRequest(prompt, model, emptyList())
            model.id.startsWith("amazon.titan") -> createTitanRequest(prompt, model)
            model.id.startsWith("meta.llama") -> createLlamaRequest(prompt, model)
            else -> throw IllegalArgumentException("Model ${model.id} does not support streaming")
        }

        val streamRequest = InvokeModelWithResponseStreamRequest {
            this.modelId = model.id
            this.contentType = "application/json"
            this.accept = "*/*"
            this.body = requestBody.toString().encodeToByteArray()
        }
        logger.debug { "Bedrock InvokeModelWithResponseStream Request (JVM): ModelID: ${model.id}, Body: $requestBody" }

        return channelFlow {
            try {
                withContext(Dispatchers.SuitableForIO) {
                    bedrockClient.invokeModelWithResponseStream(streamRequest) { response: InvokeModelWithResponseStreamResponse ->
                        response.body?.collect { event: ResponseStream ->
                            val chunkBytes = event.asChunk().bytes
                            if (chunkBytes != null) {
                                val chunkJsonString = chunkBytes.decodeToString()
                                send(chunkJsonString)
                                logger.trace { "Bedrock Stream Chunk for model ${model.id} (JVM): $chunkJsonString" }
                            } else {
                                logger.warn { "Received null chunk bytes in stream for model ${model.id} (JVM)" }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in Bedrock streaming for model ${model.id} (JVM)" }
                close(e)
            }
        }.map { chunkJsonString ->
            try {
                if (chunkJsonString.isBlank()) return@map ""

                when {
                    model.id.startsWith("anthropic.claude") -> parseAnthropicStreamChunk(chunkJsonString)
                    model.id.startsWith("amazon.titan") -> parseTitanStreamChunk(chunkJsonString)
                    model.id.startsWith("meta.llama") -> parseLlamaStreamChunk(chunkJsonString)
                    else -> ""
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse Bedrock stream chunk (JVM): $chunkJsonString" }
                ""
            }
        }
    }

    // Anthropic Claude specific methods
    private fun createAnthropicRequest(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): JsonObject {
        val messages = mutableListOf<JsonObject>()
        val systemMessages = mutableListOf<String>()

        prompt.messages.forEach { msg ->
            when (msg) {
                is Message.System -> systemMessages.add(msg.content)
                is Message.User -> {
                    // Check for image content if present
                    if (msg.mediaContent.isNotEmpty()) {
                        require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                            "Model ${model.id} does not support image input"
                        }

                        // Handle multimodal content
                        val contentParts = buildJsonArray {
                            if (msg.content.isNotEmpty()) {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", msg.content)
                                })
                            }

                            msg.mediaContent.forEach { media ->
                                when (media) {
                                    is MediaContent.Image -> {
                                        add(buildJsonObject {
                                            put("type", "image")
                                            putJsonObject("source") {
                                                put("type", "base64")
                                                put("media_type", media.getMimeType())
                                                put(
                                                    "data", if (media.isUrl()) {
                                                        // This is a simplification - in production you'd download and encode
                                                        throw IllegalArgumentException("URL images not yet supported, please provide base64 encoded images")
                                                    } else {
                                                        media.toBase64()
                                                    }
                                                )
                                            }
                                        })
                                    }

                                    else -> throw IllegalArgumentException("Unsupported media type: ${media::class.simpleName}")
                                }
                            }
                        }

                        messages.add(buildJsonObject {
                            put("role", "user")
                            put("content", contentParts)
                        })
                    } else {
                        // Text-only message
                        messages.add(buildJsonObject {
                            put("role", "user")
                            put("content", msg.content)
                        })
                    }
                }

                is Message.Assistant -> messages.add(buildJsonObject {
                    put("role", "assistant")
                    put("content", msg.content)
                })

                is Message.Tool.Call -> {
                    messages.add(buildJsonObject {
                        put("role", "assistant")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "tool_use")
                                put("id", msg.id)
                                put("name", msg.tool)
                                put("input", json.parseToJsonElement(msg.content))
                            })
                        })
                    })
                }

                is Message.Tool.Result -> {
                    messages.add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", msg.id)
                                put("content", json.parseToJsonElement(msg.content))
                            })
                        })
                    })
                }
            }
        }

        return buildJsonObject {
            put("anthropic_version", "bedrock-2023-05-31")
            put("max_tokens", 4096)

            if (systemMessages.isNotEmpty()) {
                put("system", systemMessages.joinToString("\n"))
            }

            putJsonArray("messages") {
                messages.forEach { add(it) }
            }

            // Add temperature if supported
            if (model.capabilities.contains(LLMCapability.Temperature)) {
                prompt.params.temperature?.let { put("temperature", it) }
            }

            // Add tools if provided
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            putJsonObject("input_schema") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    (tool.requiredParameters + tool.optionalParameters).forEach { param ->
                                        put(param.name, buildToolParameterSchema(param))
                                    }
                                }
                                putJsonArray("required") {
                                    tool.requiredParameters.forEach { add(json.parseToJsonElement(it.name)) }
                                }
                            }
                        })
                    }
                }

                // Handle tool choice
                when (val toolChoice = prompt.params.toolChoice) {
                    LLMParams.ToolChoice.Auto -> put("tool_choice", buildJsonObject { put("type", "auto") })
                    LLMParams.ToolChoice.None -> put("tool_choice", buildJsonObject { put("type", "none") })
                    LLMParams.ToolChoice.Required -> put("tool_choice", buildJsonObject { put("type", "any") })
                    is LLMParams.ToolChoice.Named -> put("tool_choice", buildJsonObject {
                        put("type", "tool")
                        put("name", toolChoice.name)
                    })

                    null -> {}
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun parseAnthropicResponse(responseBody: String, model: LLModel): List<Message.Response> {
        val response = json.decodeFromString<JsonObject>(responseBody)
        val content = response["content"]?.jsonArray ?: return emptyList()

        val usage = response["usage"]?.jsonObject
        val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull()
        val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull()
        val totalTokens = inputTokens?.let { input -> outputTokens?.let { output -> input + output } }

        val stopReason = response["stop_reason"]?.jsonPrimitive?.content

        return content.mapNotNull { element ->
            val item = element.jsonObject
            when (item["type"]?.jsonPrimitive?.content) {
                "text" -> Message.Assistant(
                    content = item["text"]?.jsonPrimitive?.content ?: "",
                    finishReason = stopReason,
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = totalTokens,
                        inputTokensCount = inputTokens,
                        outputTokensCount = outputTokens
                    )
                )

                "tool_use" -> Message.Tool.Call(
                    id = item["id"]?.jsonPrimitive?.content ?: Uuid.random().toString(),
                    tool = item["name"]?.jsonPrimitive?.content ?: "",
                    content = item["input"]?.toString() ?: "{}",
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = totalTokens,
                        inputTokensCount = inputTokens,
                        outputTokensCount = outputTokens
                    )
                )

                else -> null
            }
        }
    }

    private fun parseAnthropicStreamChunk(chunkJsonString: String): String {
        val chunkObj = json.decodeFromString<JsonObject>(chunkJsonString)
        val type = chunkObj["type"]?.jsonPrimitive?.content

        return when (type) {
            "content_block_delta" -> {
                val delta = chunkObj["delta"]?.jsonObject
                if (delta?.get("type")?.jsonPrimitive?.content == "text_delta") {
                    delta["text"]?.jsonPrimitive?.content ?: ""
                } else ""
            }

            "message_delta" -> {
                chunkObj["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            }

            "message_start" -> {
                val usage = chunkObj["message"]?.jsonObject?.get("usage")?.jsonObject
                val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                logger.debug { "Bedrock stream starts (JVM). Input tokens: $inputTokens" }
                ""
            }

            "message_stop" -> {
                val metrics = chunkObj["amazon-bedrock-invocationMetrics"]?.jsonObject
                val outputTokens = metrics?.get("outputTokenCount")?.jsonPrimitive?.content?.toIntOrNull()
                logger.debug { "Bedrock stream stops (JVM). Output tokens: $outputTokens" }
                ""
            }

            else -> ""
        }
    }

    // Amazon Titan specific methods
    private fun createTitanRequest(prompt: Prompt, model: LLModel): JsonObject {
        val fullPrompt = prompt.messages.joinToString("\n") { msg ->
            when (msg) {
                is Message.System -> "System: ${msg.content}"
                is Message.User -> "User: ${msg.content}"
                is Message.Assistant -> "Assistant: ${msg.content}"
                else -> ""
            }
        } + "\nAssistant:"

        return buildJsonObject {
            put("inputText", fullPrompt)
            putJsonObject("textGenerationConfig") {
                put("maxTokenCount", 4096)
                if (model.capabilities.contains(LLMCapability.Temperature)) {
                    prompt.params.temperature?.let { put("temperature", it) }
                }
            }
        }
    }

    private fun parseTitanResponse(responseBody: String): List<Message.Response> {
        val response = json.decodeFromString<JsonObject>(responseBody)
        val results = response["results"]?.jsonArray?.firstOrNull()?.jsonObject
        val outputText = results?.get("outputText")?.jsonPrimitive?.content ?: ""
        val tokenCount = results?.get("tokenCount")?.jsonPrimitive?.content?.toIntOrNull()

        return listOf(
            Message.Assistant(
                content = outputText,
                metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenCount)
            )
        )
    }

    private fun parseTitanStreamChunk(chunkJsonString: String): String {
        val chunk = json.decodeFromString<JsonObject>(chunkJsonString)
        return chunk["outputText"]?.jsonPrimitive?.content ?: ""
    }

    // AI21 Jurassic specific methods
    private fun createJurassicRequest(prompt: Prompt, model: LLModel): JsonObject {
        val fullPrompt = prompt.messages.joinToString("\n") { msg ->
            when (msg) {
                is Message.System -> msg.content
                is Message.User -> msg.content
                is Message.Assistant -> msg.content
                else -> ""
            }
        }

        return buildJsonObject {
            put("prompt", fullPrompt)
            put("maxTokens", 2048)
            if (model.capabilities.contains(LLMCapability.Temperature)) {
                prompt.params.temperature?.let { put("temperature", it) }
            }
        }
    }

    private fun parseJurassicResponse(responseBody: String): List<Message.Response> {
        val response = json.decodeFromString<JsonObject>(responseBody)
        val completions = response["completions"]?.jsonArray?.firstOrNull()?.jsonObject
        val text = completions?.get("data")?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

        return listOf(
            Message.Assistant(
                content = text,
                metaInfo = ResponseMetaInfo.create(clock)
            )
        )
    }

    // Cohere Command specific methods
    private fun createCohereRequest(prompt: Prompt, model: LLModel): JsonObject {
        val text = prompt.messages.joinToString("\n") { msg ->
            when (msg) {
                is Message.System -> msg.content
                is Message.User -> msg.content
                is Message.Assistant -> msg.content
                else -> ""
            }
        }

        return buildJsonObject {
            put("prompt", text)
            put("max_tokens", 2048)
            if (model.capabilities.contains(LLMCapability.Temperature)) {
                prompt.params.temperature?.let { put("temperature", it) }
            }
        }
    }

    private fun parseCohereResponse(responseBody: String): List<Message.Response> {
        val response = json.decodeFromString<JsonObject>(responseBody)
        val generations = response["generations"]?.jsonArray?.firstOrNull()?.jsonObject
        val text = generations?.get("text")?.jsonPrimitive?.content ?: ""

        return listOf(
            Message.Assistant(
                content = text,
                metaInfo = ResponseMetaInfo.create(clock)
            )
        )
    }

    // Meta Llama specific methods
    private fun createLlamaRequest(prompt: Prompt, model: LLModel): JsonObject {
        val promptText = prompt.messages.joinToString("\n") { msg ->
            when (msg) {
                is Message.System -> "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n${msg.content}<|eot_id|>"
                is Message.User -> "<|start_header_id|>user<|end_header_id|>\n\n${msg.content}<|eot_id|>"
                is Message.Assistant -> "<|start_header_id|>assistant<|end_header_id|>\n\n${msg.content}<|eot_id|>"
                else -> ""
            }
        } + "<|start_header_id|>assistant<|end_header_id|>\n\n"

        return buildJsonObject {
            put("prompt", promptText)
            put("max_gen_len", 2048)
            if (model.capabilities.contains(LLMCapability.Temperature)) {
                prompt.params.temperature?.let { put("temperature", it) }
            }
        }
    }

    private fun parseLlamaResponse(responseBody: String): List<Message.Response> {
        val response = json.decodeFromString<JsonObject>(responseBody)
        val generation = response["generation"]?.jsonPrimitive?.content ?: ""
        val promptTokenCount = response["prompt_token_count"]?.jsonPrimitive?.content?.toIntOrNull()
        val generationTokenCount = response["generation_token_count"]?.jsonPrimitive?.content?.toIntOrNull()
        val stopReason = response["stop_reason"]?.jsonPrimitive?.content

        return listOf(
            Message.Assistant(
                content = generation,
            finishReason = stopReason,
            metaInfo = ResponseMetaInfo.create(
                clock,
                inputTokensCount = promptTokenCount,
                outputTokensCount = generationTokenCount,
                totalTokensCount = promptTokenCount?.let { input ->
                    generationTokenCount?.let { output -> input + output }
                }
            )
        ))
    }

    private fun parseLlamaStreamChunk(chunkJsonString: String): String {
        val chunk = json.decodeFromString<JsonObject>(chunkJsonString)
        return chunk["generation"]?.jsonPrimitive?.content ?: ""
    }

    // Helper method to build tool parameter schema
    private fun buildToolParameterSchema(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", param.description)

        when (val type = param.type) {
            ToolParameterType.Boolean -> put("type", "boolean")
            ToolParameterType.Float -> put("type", "number")
            ToolParameterType.Integer -> put("type", "integer")
            ToolParameterType.String -> put("type", "string")

            is ToolParameterType.Enum -> {
                put("type", "string")
                putJsonArray("enum") { type.entries.forEach { add(json.parseToJsonElement(it)) } }
            }

            is ToolParameterType.List -> {
                put("type", "array")
                putJsonObject("items") {
                    when (type.itemsType) {
                        ToolParameterType.Boolean -> put("type", "boolean")
                        ToolParameterType.Float -> put("type", "number")
                        ToolParameterType.Integer -> put("type", "integer")
                        ToolParameterType.String -> put("type", "string")
                        else -> put("type", "string")
                    }
                }
            }

            is ToolParameterType.Object -> {
                put("type", "object")
                putJsonObject("properties") {
                    type.properties.forEach { prop ->
                        put(prop.name, buildToolParameterSchema(prop))
                    }
                }
            }
        }
    }
}
