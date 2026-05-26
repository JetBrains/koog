package ai.koog.spring.ai.chat

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.content.Media
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import org.springframework.util.MimeType
import java.net.URI
import org.springframework.ai.chat.messages.Message as SpringMessage
import org.springframework.ai.moderation.Moderation as SpringModeration

private val converterLogger = LoggerFactory.getLogger("ai.koog.spring.ai.chat.Converters")

/**
 * Converts a list of Koog [Message]s to a list of Spring AI [SpringMessage]s.
 *
 * @param messages the Koog messages to convert
 * @return the corresponding Spring AI messages
 * @throws IllegalArgumentException if a message type is not supported
 */
public fun koogMessageToSpringMessage(messages: List<Message>): List<SpringMessage> {
    return buildList {
        messages.forEach { message ->
            when (message) {
                is Message.System -> {
                    add(SystemMessage(message.parts.joinToString(separator = "\n") { it.text }))
                }

                is Message.User -> {
                    val textContent = message.parts.filterIsInstance<MessagePart.Text>()
                        .joinToString("\n") { it.text }
                    val media = message.parts.filterIsInstance<MessagePart.Attachment>()
                        .map { attachmentToSpringMedia(it.source) }
                    val toolResults = message.parts.filterIsInstance<MessagePart.Tool.Result>()

                    if (textContent.isNotEmpty() || media.isNotEmpty()) {
                        val builder = UserMessage.builder().text(textContent)
                        if (media.isNotEmpty()) builder.media(media)
                        add(builder.build())
                    }

                    toolResults.forEach { toolResult ->
                        val toolResponse = ToolResponseMessage.ToolResponse(
                            toolResult.id ?: "",
                            toolResult.tool,
                            toolResult.output
                        )
                        add(
                            ToolResponseMessage.builder()
                                .responses(listOf(toolResponse))
                                .build()
                        )
                    }
                }

                is Message.Assistant -> {
                    val textContent = message.parts.filterIsInstance<MessagePart.Text>()
                        .joinToString("") { it.text }
                    val springToolCalls = message.parts.filterIsInstance<MessagePart.Tool.Call>()
                        .map { part ->
                            AssistantMessage.ToolCall(
                                part.id ?: "",
                                "function",
                                part.tool,
                                part.args
                            )
                        }
                    val reasoningContent = message.parts.filterIsInstance<MessagePart.Reasoning>()
                        .flatMap { it.content }
                        .joinToString("")

                    val builder = AssistantMessage.builder()
                        .content(textContent)
                        .toolCalls(springToolCalls)
                    if (reasoningContent.isNotEmpty()) {
                        builder.properties(mapOf("reasoningContent" to reasoningContent))
                    }
                    add(builder.build())
                }
            }
        }
    }
}

/**
 * Converts a single Koog [Message] to a Spring AI [SpringMessage].
 *
 * @param message the Koog message to convert
 * @return the corresponding Spring AI message
 * @throws NoSuchElementException if the message produces no Spring AI output (e.g. a [Message.User]
 *   containing only tool results with no text or media content)
 */
public fun koogMessageToSpringMessage(message: Message): SpringMessage {
    return koogMessageToSpringMessage(listOf(message)).first()
}

/**
 * Converts a Spring AI [Generation] (from a [org.springframework.ai.chat.model.ChatResponse]) to a Koog [Message.Assistant].
 *
 * If the generation contains tool calls, each tool call is converted to a [MessagePart.Tool.Call].
 * Otherwise, the text content is converted to a [MessagePart.Text].
 *
 * @param generation the Spring AI generation to convert
 * @param clock the clock to use for creating response metadata timestamps
 * @param usage optional token usage information from the chat response metadata
 * @return a Koog [Message.Assistant]
 */
public fun springGenerationToKoogResponses(
    generation: Generation,
    clock: KoogClock = KoogClock.System,
    usage: Usage? = null
): Message.Assistant {
    val assistantMessage = generation.output
    val metaInfo = ResponseMetaInfo.create(
        clock = clock,
        totalTokensCount = usage?.totalTokens,
        inputTokensCount = usage?.promptTokens,
        outputTokensCount = usage?.completionTokens
    )
    val finishReason = assistantMessage.metadata["finishReason"]?.toString()
    val parts = buildList {
        assistantMessage.metadata["reasoningContent"]
            ?.toString()
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(MessagePart.Reasoning(content = it)) }

        assistantMessage.text
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(MessagePart.Text(it)) }

        if (assistantMessage.hasToolCalls()) {
            assistantMessage.toolCalls.forEach { toolCall ->
                add(
                    MessagePart.Tool.Call(
                        id = toolCall.id(),
                        tool = toolCall.name(),
                        args = Json.decodeFromString<JsonObject>(toolCall.arguments()).jsonObject,
                    )
                )
            }
        }
    }

    return Message.Assistant(parts = parts, finishReason = finishReason, metaInfo = metaInfo)
}

/**
 * Converts a Koog [ToolDescriptor] to a Spring AI [ToolCallback].
 *
 * The returned callback carries the tool definition (name, description, JSON schema)
 * but its [ToolCallback.call] method always throws [IllegalStateException].
 * This is by design: in KOOG execution mode, tools are executed by the Koog agent
 * framework, not by Spring AI. The `internalToolExecutionEnabled=false` option
 * prevents Spring from ever calling this method. If it is called anyway, the error
 * message provides clear guidance on the misconfiguration.
 *
 * @param descriptor the Koog tool descriptor to convert
 * @return the corresponding Spring AI tool callback (definition-only, non-executable)
 */
public fun koogToolDescriptorToToolCallback(descriptor: ToolDescriptor): ToolCallback {
    val jsonSchema = toolDescriptorToJsonSchema(descriptor)
    return object : ToolCallback {
        override fun getToolDefinition(): ToolDefinition {
            return ToolDefinition.builder().name(descriptor.name)
                .description(descriptor.description)
                .inputSchema(jsonSchema.toString())
                .build()
        }

        override fun call(toolInput: String): String {
            converterLogger.error(
                "Spring AI attempted to execute tool '{}' via callback. " +
                    "This should never happen when Koog owns tool execution " +
                    "(internalToolExecutionEnabled=false). " +
                    "Check your Spring AI / Koog configuration.",
                descriptor.name
            )
            throw IllegalStateException(
                "Tool '${descriptor.name}' execution is handled by the Koog agent framework, " +
                    "not Spring AI. If you see this error, ensure that " +
                    "ToolCallingChatOptions.internalToolExecutionEnabled is set to false. " +
                    "This is a configuration error."
            )
        }
    }
}

/**
 * Converts a [ToolDescriptor] to a JSON Schema [JsonObject] representing its input parameters.
 *
 * Uses [kotlinx.serialization.json] builders to produce well-formed JSON with proper escaping.
 */
internal fun toolDescriptorToJsonSchema(descriptor: ToolDescriptor): JsonObject {
    val allParams = descriptor.requiredParameters + descriptor.optionalParameters
    val requiredNames = descriptor.requiredParameters.map { it.name }

    val schema = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            JsonObject(
                allParams.associate { param ->
                    param.name to parameterTypeToJsonElement(param.type, param.description)
                }
            )
        )
        if (requiredNames.isNotEmpty()) {
            put("required", JsonArray(requiredNames.map { JsonPrimitive(it) }))
        }
    }

    return schema
}

/**
 * Converts a [ToolParameterType] to a [JsonElement] representing its JSON Schema.
 *
 * @param type the parameter type
 * @param description optional parameter description to include in the schema
 */
internal fun parameterTypeToJsonElement(
    type: ToolParameterType,
    description: String? = null
): JsonElement = buildJsonObject {
    when (type) {
        is ToolParameterType.String -> put("type", "string")
        is ToolParameterType.Integer -> put("type", "integer")
        is ToolParameterType.Float -> put("type", "number")
        is ToolParameterType.Boolean -> put("type", "boolean")
        is ToolParameterType.Null -> put("type", "null")
        is ToolParameterType.Enum -> {
            put("type", "string")
            put("enum", JsonArray(type.entries.map { JsonPrimitive(it) }))
        }

        is ToolParameterType.List -> {
            put("type", "array")
            put("items", parameterTypeToJsonElement(type.itemsType))
        }

        is ToolParameterType.Object -> {
            put("type", "object")
            put(
                "properties",
                JsonObject(
                    type.properties.associate { prop ->
                        prop.name to parameterTypeToJsonElement(prop.type, prop.description)
                    }
                )
            )
            if (type.requiredProperties.isNotEmpty()) {
                put("required", JsonArray(type.requiredProperties.map { JsonPrimitive(it) }))
            }
        }

        is ToolParameterType.AnyOf -> {
            put("anyOf", JsonArray(type.types.map { parameterTypeToJsonElement(it.type) }))
        }
    }
    if (!description.isNullOrBlank()) {
        put("description", description)
    }
}

/**
 * Converts a Spring AI [SpringModeration] to a Koog [ModerationResult].
 *
 * Maps all 11 Spring AI moderation categories to their Koog counterparts,
 * carrying both the detected flag and the confidence score.
 *
 * If the Spring AI result contains no individual results, returns a non-harmful
 * [ModerationResult] with an empty category map.
 *
 * @param springResult the Spring AI moderation result to convert
 * @return the corresponding Koog moderation result
 */
public fun springModerationResultToKoogModerationResult(springResult: SpringModeration): ModerationResult {
    if (springResult.results.isEmpty()) {
        return ModerationResult(isHarmful = false, categories = emptyMap())
    }

    val result = springResult.results.first()
    val cats = result.categories
    val scores = result.categoryScores

    val categoryMap = buildMap {
        put(
            ModerationCategory.Harassment,
            ModerationCategoryResult(
                detected = cats.isHarassment,
                confidenceScore = scores.harassment
            )
        )
        put(
            ModerationCategory.HarassmentThreatening,
            ModerationCategoryResult(
                detected = cats.isHarassmentThreatening,
                confidenceScore = scores.harassmentThreatening
            )
        )
        put(
            ModerationCategory.Hate,
            ModerationCategoryResult(
                detected = cats.isHate,
                confidenceScore = scores.hate
            )
        )
        put(
            ModerationCategory.HateThreatening,
            ModerationCategoryResult(
                detected = cats.isHateThreatening,
                confidenceScore = scores.hateThreatening
            )
        )
        put(
            ModerationCategory.Sexual,
            ModerationCategoryResult(
                detected = cats.isSexual,
                confidenceScore = scores.sexual
            )
        )
        put(
            ModerationCategory.SexualMinors,
            ModerationCategoryResult(
                detected = cats.isSexualMinors,
                confidenceScore = scores.sexualMinors
            )
        )
        put(
            ModerationCategory.Violence,
            ModerationCategoryResult(
                detected = cats.isViolence,
                confidenceScore = scores.violence
            )
        )
        put(
            ModerationCategory.ViolenceGraphic,
            ModerationCategoryResult(
                detected = cats.isViolenceGraphic,
                confidenceScore = scores.violenceGraphic
            )
        )
        put(
            ModerationCategory.SelfHarm,
            ModerationCategoryResult(
                detected = cats.isSelfHarm,
                confidenceScore = scores.selfHarm
            )
        )
        put(
            ModerationCategory.SelfHarmIntent,
            ModerationCategoryResult(
                detected = cats.isSelfHarmIntent,
                confidenceScore = scores.selfHarmIntent
            )
        )
        put(
            ModerationCategory.SelfHarmInstructions,
            ModerationCategoryResult(
                detected = cats.isSelfHarmInstructions,
                confidenceScore = scores.selfHarmInstructions
            )
        )
    }

    return ModerationResult(isHarmful = result.isFlagged, categories = categoryMap)
}

/**
 * Converts a Koog [AttachmentSource] to a Spring AI [Media] object.
 *
 * Supports URL-based, binary (base64/bytes), and plain-text attachment content.
 *
 * @param attachment the Koog attachment to convert
 * @return the corresponding Spring AI Media
 */
internal fun attachmentToSpringMedia(attachment: AttachmentSource): Media {
    val mimeType = try {
        MimeType.valueOf(attachment.mimeType)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(
            "Invalid MIME type '${attachment.mimeType}' in attachment of type '${attachment.content::class.simpleName}'",
            e
        )
    }
    return when (val content = attachment.content) {
        is AttachmentContent.URL -> {
            val uri = try {
                URI.create(content.url)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Invalid URL '${content.url}' in attachment with MIME type '${attachment.mimeType}'",
                    e
                )
            }
            Media.builder()
                .mimeType(mimeType)
                .data(uri)
                .build()
        }

        is AttachmentContent.Binary -> Media.builder()
            .mimeType(mimeType)
            .data(content.asBytes())
            .build()

        is AttachmentContent.PlainText -> Media.builder()
            .mimeType(mimeType)
            .data(content.text.toByteArray(Charsets.UTF_8))
            .build()
    }
}
