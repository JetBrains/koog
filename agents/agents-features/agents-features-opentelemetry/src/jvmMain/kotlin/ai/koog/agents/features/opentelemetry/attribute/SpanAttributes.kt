package ai.koog.agents.features.opentelemetry.attribute

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * The class describe Attributes related to a Spans in GenAI system.
 *
 * The list of supported attributes according to Open Telemetry Semantic Convention
 * (https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/)
 *
 * Note: Some shared attributes are located in [CommonAttributes] class.
 *
 * List of attributes:
 * - gen_ai.operation.name (required)
 * - gen_ai.agent.description (conditional)
 * - gen_ai.agent.id (conditional)
 * - gen_ai.agent.name (conditional)
 * - gen_ai.conversation.id (conditional)
 * - gen_ai.data_source.id (conditional)
 * - gen_ai.output.type (conditional/required)
 * - gen_ai.request.choice.count (conditional/required)
 * - gen_ai.request.model (conditional/required)
 * - gen_ai.request.seed (conditional/required)
 * - gen_ai.request.frequency_penalty (recommended)
 * - gen_ai.request.max_tokens (recommended)
 * - gen_ai.request.presence_penalty (recommended)
 * - gen_ai.request.stop_sequences (recommended)
 * - gen_ai.request.temperature (recommended)
 * - gen_ai.request.top_p (recommended)
 * - gen_ai.response.finish_reasons (recommended)
 * - gen_ai.response.id (recommended)
 * - gen_ai.response.model (recommended)
 * - gen_ai.usage.input_tokens (recommended)
 * - gen_ai.usage.output_tokens (recommended)
 * - gen_ai.tool.call.id (recommended)
 * - gen_ai.tool.description (recommended)
 * - gen_ai.tool.name (recommended)
 */
internal object SpanAttributes {

    // gen_ai.operation
    sealed interface Operation : GenAIAttribute {

        override val key: String
            get() = super.key.concatKey("operation")

        // gen_ai.operation.name
        data class Name(private val operation: OperationNameType) : Operation {
            override val key: String = super.key.concatKey("name")
            override val value: String = operation.id
        }

        enum class OperationNameType(val id: String) {
            CHAT("chat"),
            CREATE_AGENT("create_agent"),
            EMBEDDINGS("embeddings"),
            EXECUTE_TOOL("execute_tool"),
            GENERATE_CONTENT("generate_content"),
            INVOKE_AGENT("invoke_agent"),
            TEXT_COMPLETION("text_completion"),
        }
    }

    // gen_ai.agent
    sealed interface Agent : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("agent")

        // gen_ai.agent.description
        data class Description(private val description: String) : Agent {
            override val key: String = super.key.concatKey("description")
            override val value: String = description
        }

        // gen_ai.agent.id
        data class Id(private val id: String) : Agent {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }

        // gen_ai.agent.name
        data class Name(private val name: String) : Agent {
            override val key: String = super.key.concatKey("name")
            override val value: String = name
        }
    }

    // gen_ai.provider

    sealed interface Provider : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("provider")

        data class Name(private val provider: LLMProvider) : Provider {
            override val key: String = super.key.concatKey("name")
            override val value: String = provider.id
        }
    }

    // gen_ai.conversation
    sealed interface Conversation : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("conversation")

        // gen_ai.conversation.id
        data class Id(private val id: String) : Conversation {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }
    }

    // gen_ai.data_source
    sealed interface DataSource : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("data_source")

        // gen_ai.data_source.id
        data class Id(private val id: String) : DataSource {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }
    }

    // gen_ai.input
    sealed interface Input : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("input")

        // gen_ai.input.messages
        data class Messages(private val messages: List<Message>) : Input {
            override val key: String = super.key.concatKey("messages")
            override val value: HiddenString = HiddenString(
                JsonArray(
                    messages.map { message ->
                        buildJsonObject {
                            put("role", JsonPrimitive(message.role.name))
                            putJsonArray("parts") {
                                message.parts.forEach { part ->
                                    addContentPart(part, message)
                                }
                            }
                        }
                    }
                ).toString()
            )
        }
    }

    // gen_ai.output
    sealed interface Output : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("output")

        // gen_ai.output.type
        data class Type(private val type: OutputType) : Output {
            override val key: String = super.key.concatKey("type")
            override val value: String = type.id
        }

        // gen_ai.output.messages
        data class Messages(private val messages: List<Message>) : Output {
            override val key: String = super.key.concatKey("messages")
            override val value: HiddenString = HiddenString(
                JsonArray(
                    messages.map { message ->
                        buildJsonObject {
                            put("role", JsonPrimitive(message.role.name))
                            putJsonArray("parts") {
                                message.parts.forEach { part ->
                                    addContentPart(part, message)
                                }
                            }
                        }
                    }
                ).toString()
            )
        }

        enum class OutputType(val id: String) {
            TEXT("text"),
            JSON("json"),
            IMAGE("image"),
        }
    }

    // gen_ai.request
    sealed interface Request : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("request")

        // gen_ai.request.choice
        sealed interface Choice : Request {
            override val key: String
                get() = super.key.concatKey("choice")

            // gen_ai.request.choice.count
            data class Count(private val count: Int) : Choice {
                override val key: String = super.key.concatKey("count")
                override val value: Int = count
            }
        }

        // gen_ai.request.model
        data class Model(private val model: LLModel) : Request {
            override val key: String = super.key.concatKey("model")
            override val value: String = model.id
        }

        // gen_ai.request.seed
        data class Seed(private val seed: Int) : Request {
            override val key: String = super.key.concatKey("seed")
            override val value: Int = seed
        }

        // gen_ai.request.frequency_penalty
        data class FrequencyPenalty(private val frequencyPenalty: Double) : Request {
            override val key: String = super.key.concatKey("frequency_penalty")
            override val value: Double = frequencyPenalty
        }

        // gen_ai.request.max_tokens
        data class MaxTokens(private val maxTokens: Int) : Request {
            override val key: String = super.key.concatKey("max_tokens")
            override val value: Int = maxTokens
        }

        // gen_ai.request.presence_penalty
        data class PresencePenalty(private val presencePenalty: Double) : Request {
            override val key: String = super.key.concatKey("presence_penalty")
            override val value: Double = presencePenalty
        }

        // gen_ai.request.stop_sequences
        data class StopSequences(private val stopSequences: List<String>) : Request {
            override val key: String = super.key.concatKey("stop_sequences")
            override val value: List<String> = stopSequences
        }

        // gen_ai.request.temperature
        data class Temperature(private val temperature: Double) : Request {
            override val key: String = super.key.concatKey("temperature")
            override val value: Double = temperature
        }

        // gen_ai.request.top_p
        data class TopP(private val topP: Double) : Request {
            override val key: String = super.key.concatKey("top_p")
            override val value: Double = topP
        }
    }

    // gen_ai.response
    sealed interface Response : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("response")

        // gen_ai.response.finish_reasons
        data class FinishReasons(private val reasons: List<FinishReasonType>) : Response {
            override val key: String = super.key.concatKey("finish_reasons")
            override val value: List<String> = reasons.map { it.id }
        }

        sealed interface FinishReasonType {
            val id: String
            object ContentFilter : FinishReasonType {
                override val id = "content_filter"
            }
            object Error : FinishReasonType {
                override val id = "error"
            }
            object Length : FinishReasonType {
                override val id = "length"
            }
            object Stop : FinishReasonType {
                override val id = "stop"
            }
            object ToolCalls : FinishReasonType {
                override val id = "tool_calls"
            }
            data class Custom(override val id: String) : FinishReasonType
        }

        // gen_ai.response.id
        data class Id(private val id: String) : Response {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }

        // gen_ai.response.model
        data class Model(private val model: LLModel) : Response {
            override val key: String = super.key.concatKey("model")
            override val value: String = model.id
        }
    }

    // gen_ai.usage
    sealed interface Usage : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("usage")

        // gen_ai.usage.input_tokens
        data class InputTokens(private val tokens: Int) : Usage {
            override val key: String = super.key.concatKey("input_tokens")
            override val value: Int = tokens
        }

        // gen_ai.usage.output_tokens
        data class OutputTokens(private val tokens: Int) : Usage {
            override val key: String = super.key.concatKey("output_tokens")
            override val value: Int = tokens
        }

        // gen_ai.usage.total_tokens
        // Note: Non-semantic attribute
        data class TotalTokens(private val tokens: Int) : Usage {
            override val key: String = super.key.concatKey("total_tokens")
            override val value: Int = tokens
        }
    }

    // gen_ai.tool
    sealed interface Tool : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("tool")

        // gen_ai.tool.call
        sealed interface Call : Tool {
            override val key: String
                get() = super.key.concatKey("call")

            // gen_ai.tool.call.id
            data class Id(private val id: String) : Call {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }

            // gen_ai.tool.call.arguments
            data class Arguments(private val arguments: JsonObject) : Call {
                override val key: String = super.key.concatKey("arguments")
                override val value: HiddenString = HiddenString(arguments.toString())
            }

            // gen_ai.tool.call.result
            data class Result(private val result: JsonElement) : Call {
                override val key: String = super.key.concatKey("result")
                override val value: HiddenString = HiddenString(result.toString())
            }
        }

        // gen_ai.tool.description
        data class Description(private val description: String) : Tool {
            override val key: String = super.key.concatKey("description")
            override val value: String = description
        }

        // gen_ai.tool.name
        data class Name(private val name: String) : Tool {
            override val key: String = super.key.concatKey("name")
            override val value: String = name
        }

        // gen_ai.tool.definitions
        data class Definitions(private val tools: List<ToolDescriptor>) : Tool {
            override val key: String = super.key.concatKey("definitions")
            override val value: HiddenString = HiddenString(
                JsonArray(
                    tools.map { tool ->
                        buildJsonObject {
                            put("type", JsonPrimitive("function"))
                            put("name", JsonPrimitive(tool.name))
                            put("description", JsonPrimitive(tool.description))
                        }
                    }
                ).toString()
            )
        }
    }

    // gen_ai.system_instructions
    data class SystemInstructions(private val messages: List<Message.System>) : GenAIAttribute {
        override val key: String = "system_instructions"
        override val value: HiddenString = run {
            val jsonObjects = messages.flatMap { (parts, metaInfo) ->
                parts.map { part ->
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("text"),
                            "content" to JsonPrimitive(part.text)
                        )
                    )
                }
            }

            HiddenString(JsonArray(jsonObjects).toString())
        }
    }

    //region Private Methods

    private fun JsonArrayBuilder.addContentPart(part: ContentPart, message: Message) {
        when (part) {
            is ContentPart.Text -> {
                when (message) {
                    is Message.Tool.Call -> {
                        addJsonObject {
                            put("type", JsonPrimitive("tool_call"))
                            message.id?.let { id -> put("id", JsonPrimitive(id)) }
                            put("name", JsonPrimitive(message.tool))
                            put("arguments", message.contentJson)
                        }
                    }

                    is Message.Tool.Result -> {
                        addJsonObject {
                            put("type", JsonPrimitive("tool_call_response"))
                            message.id?.let { id -> put("id", JsonPrimitive(id)) }
                            put("result", JsonPrimitive(part.text))
                        }
                    }

                    else -> {
                        addJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("content", JsonPrimitive(part.text))
                        }
                    }
                }
            }

            is ContentPart.Image -> {
                addJsonObject {
                    put("type", JsonPrimitive("image"))
                    put("format", JsonPrimitive(part.format))
                    put("mimeType", JsonPrimitive(part.mimeType))
                    part.fileName?.let { name -> put("fileName", JsonPrimitive(name)) }
                }
            }

            is ContentPart.Video -> {
                addJsonObject {
                    put("type", JsonPrimitive("video"))
                    put("format", JsonPrimitive(part.format))
                    put("mimeType", JsonPrimitive(part.mimeType))
                    part.fileName?.let { name -> put("fileName", JsonPrimitive(name)) }
                }
            }

            is ContentPart.Audio -> {
                addJsonObject {
                    put("type", JsonPrimitive("audio"))
                    put("format", JsonPrimitive(part.format))
                    put("mimeType", JsonPrimitive(part.mimeType))
                    part.fileName?.let { name -> put("fileName", JsonPrimitive(name)) }
                }
            }

            is ContentPart.File -> {
                addJsonObject {
                    put("type", JsonPrimitive("file"))
                    put("format", JsonPrimitive(part.format))
                    put("mimeType", JsonPrimitive(part.mimeType))
                    part.fileName?.let { name -> put("fileName", JsonPrimitive(name)) }
                }
            }
        }
    }

    //endregion Private Methods
}
