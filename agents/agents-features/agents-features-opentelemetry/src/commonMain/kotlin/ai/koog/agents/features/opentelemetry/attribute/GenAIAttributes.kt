package ai.koog.agents.features.opentelemetry.attribute

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * This class describes attributes in the GenAI system.
 *
 * The list of supported attributes according to OpenTelemetry Semantic Convention
 * (https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/)
 *
 * Note: Some shared attributes are located in [CommonAttributes] class.
 *
 * List of attributes:
 * - gen_ai.operation.name (required)
 * - gen_ai.agent.description (conditional)
 * - gen_ai.agent.id (conditional)
 * - gen_ai.agent.name (conditional)
 * - gen_ai.provider.name (conditional)
 * - gen_ai.conversation.id (conditional)
 * - gen_ai.data_source.id (conditional)
 * - gen_ai.input.messages (recommended)
 * - gen_ai.output.type (conditional/required)
 * - gen_ai.output.messages (recommended)
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
 * - gen_ai.token.type (required)
 * - gen_ai.usage.input_tokens (recommended)
 * - gen_ai.usage.output_tokens (recommended)
 * - gen_ai.usage.total_tokens (non-semantic)
 * - gen_ai.tool.call.id (recommended)
 * - gen_ai.tool.call.arguments (recommended)
 * - gen_ai.tool.call.result (recommended)
 * - gen_ai.tool.description (recommended)
 * - gen_ai.tool.name (recommended)
 * - gen_ai.tool.definitions (recommended)
 * - gen_ai.system_instructions (recommended)
 */
public object GenAIAttributes {

    /**
     * `gen_ai.operation` attribute namespace.
     */
    public sealed interface Operation : GenAIAttribute {

        override val key: String
            get() = super.key.concatKey("operation")

        /**
         * `gen_ai.operation.name` attribute.
         */
        public data class Name(public val operation: OperationNameType) : Operation {
            override val key: String = super.key.concatKey("name")
            override val value: String = operation.id
        }

        /**
         * Allowed values for `gen_ai.operation.name` per the OTel GenAI semantic conventions.
         */
        public enum class OperationNameType(public val id: String) {
            CHAT("chat"),
            CREATE_AGENT("create_agent"),
            EMBEDDINGS("embeddings"),
            EXECUTE_TOOL("execute_tool"),
            GENERATE_CONTENT(
                "generate_content"
            ),
            INVOKE_AGENT("invoke_agent"),
            TEXT_COMPLETION("text_completion"),
        }
    }

    /**
     * `gen_ai.agent` attribute namespace.
     */
    public sealed interface Agent : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("agent")

        /**
         * `gen_ai.agent.description` attribute.
         */
        public data class Description(public val description: String) : Agent {
            override val key: String = super.key.concatKey("description")
            override val value: String = description
        }

        /**
         * `gen_ai.agent.id` attribute.
         */
        public data class Id(public val id: String) : Agent {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }

        /**
         * `gen_ai.agent.name` attribute.
         */
        public data class Name(public val name: String) : Agent {
            override val key: String = super.key.concatKey("name")
            override val value: String = name
        }
    }

    /**
     * `gen_ai.provider` attribute namespace.
     */
    public sealed interface Provider : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("provider")

        /**
         * `gen_ai.provider.name` attribute.
         */
        public data class Name(override val value: String) : Provider {
            override val key: String = super.key.concatKey("name")

            public constructor(provider: LLMProvider) : this(provider.id)
        }
    }

    /**
     * `gen_ai.conversation` attribute namespace.
     */
    public sealed interface Conversation : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("conversation")

        /**
         * `gen_ai.conversation.id` attribute.
         */
        public data class Id(public val id: String) : Conversation {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }
    }

    /**
     * `gen_ai.data_source` attribute namespace.
     */
    public sealed interface DataSource : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("data_source")

        /**
         * `gen_ai.data_source.id` attribute.
         */
        public data class Id(public val id: String) : DataSource {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }
    }

    /**
     * `gen_ai.input` attribute namespace.
     */
    public sealed interface Input : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("input")

        /**
         * `gen_ai.input.messages` attribute.
         */
        public data class Messages(public val messages: List<Message>) : Input {
            override val key: String = super.key.concatKey("messages")
            override val value: HiddenString = HiddenString(messages.toMessagesJsonString())
        }
    }

    /**
     * `gen_ai.output` attribute namespace.
     */
    public sealed interface Output : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("output")

        /**
         * `gen_ai.output.type` attribute.
         */
        public data class Type(public val type: OutputType) : Output {
            override val key: String = super.key.concatKey("type")
            override val value: String = type.id
        }

        /**
         * `gen_ai.output.messages` attribute.
         */
        public data class Messages(public val messages: List<Message>) : Output {
            override val key: String = super.key.concatKey("messages")
            override val value: HiddenString = HiddenString(messages.toMessagesJsonString())
        }

        /**
         * Allowed values for `gen_ai.output.type` per the OTel GenAI semantic conventions.
         */
        public enum class OutputType(public val id: String) {
            TEXT("text"),
            JSON("json"),
            IMAGE("image"),
        }
    }

    /**
     * `gen_ai.request` attribute namespace.
     */
    public sealed interface Request : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("request")

        /**
         * `gen_ai.request.choice` attribute namespace.
         */
        public sealed interface Choice : Request {
            override val key: String
                get() = super.key.concatKey("choice")

            /**
             * `gen_ai.request.choice.count` attribute.
             */
            public data class Count(public val count: Int) : Choice {
                override val key: String = super.key.concatKey("count")
                override val value: Int = count
            }
        }

        /**
         * `gen_ai.request.model` attribute.
         */
        public data class Model(public val model: LLModel) : Request {
            override val key: String = super.key.concatKey("model")
            override val value: String = model.id
        }

        /**
         * `gen_ai.request.seed` attribute.
         */
        public data class Seed(public val seed: Int) : Request {
            override val key: String = super.key.concatKey("seed")
            override val value: Int = seed
        }

        /**
         * `gen_ai.request.frequency_penalty` attribute.
         */
        public data class FrequencyPenalty(public val frequencyPenalty: Double) : Request {
            override val key: String = super.key.concatKey("frequency_penalty")
            override val value: Double = frequencyPenalty
        }

        /**
         * `gen_ai.request.max_tokens` attribute.
         */
        public data class MaxTokens(public val maxTokens: Int) : Request {
            override val key: String = super.key.concatKey("max_tokens")
            override val value: Int = maxTokens
        }

        /**
         * `gen_ai.request.presence_penalty` attribute.
         */
        public data class PresencePenalty(public val presencePenalty: Double) : Request {
            override val key: String = super.key.concatKey("presence_penalty")
            override val value: Double = presencePenalty
        }

        /**
         * `gen_ai.request.stop_sequences` attribute.
         */
        public data class StopSequences(public val stopSequences: List<String>) : Request {
            override val key: String = super.key.concatKey("stop_sequences")
            override val value: List<String> = stopSequences
        }

        /**
         * `gen_ai.request.temperature` attribute.
         */
        public data class Temperature(public val temperature: Double) : Request {
            override val key: String = super.key.concatKey("temperature")
            override val value: Double = temperature
        }

        /**
         * `gen_ai.request.top_p` attribute.
         */
        public data class TopP(public val topP: Double) : Request {
            override val key: String = super.key.concatKey("top_p")
            override val value: Double = topP
        }
    }

    /**
     * `gen_ai.response` attribute namespace.
     */
    public sealed interface Response : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("response")

        /**
         * `gen_ai.response.finish_reasons` attribute.
         */
        public data class FinishReasons(public val reasons: List<FinishReasonType>) : Response {
            override val key: String = super.key.concatKey("finish_reasons")
            override val value: List<String> = reasons.map { it.id }
        }

        /**
         * Allowed values for `gen_ai.response.finish_reasons`. The standard set covers
         * `content_filter`, `length`, `stop`, and `tool_calls`; use [Custom] for vendor-specific
         * reasons.
         */
        public sealed interface FinishReasonType {
            /**
             * Wire identifier emitted on the span attribute.
             */
            public val id: String

            /**
             * Generation stopped because content was filtered.
             */
            public object ContentFilter : FinishReasonType {
                override val id: String = "content_filter"
            }

            /**
             * Generation stopped because the maximum length was reached.
             */
            public object Length : FinishReasonType {
                override val id: String = "length"
            }

            /**
             * Generation stopped naturally (end of response).
             */
            public object Stop : FinishReasonType {
                override val id: String = "stop"
            }

            /**
             * Generation stopped because the model emitted tool calls.
             */
            public object ToolCalls : FinishReasonType {
                override val id: String = "tool_calls"
            }

            /**
             * Vendor-specific finish reason carried as a verbatim string.
             */
            public data class Custom(override val id: String) : FinishReasonType
        }

        /**
         * `gen_ai.response.id` attribute.
         */
        public data class Id(public val id: String) : Response {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }

        /**
         * `gen_ai.response.model` attribute.
         */
        public data class Model(public val model: LLModel) : Response {
            override val key: String = super.key.concatKey("model")
            override val value: String = model.id
        }

        /**
         * `gen_ai.response.metadata` attribute.
         */
        public data class Metadata(public val metadata: String) : Response {
            override val key: String = super.key.concatKey("metadata")
            override val value: String = metadata
        }
    }

    /**
     * `gen_ai.token` attribute namespace.
     */
    public sealed interface Token : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("token")

        /**
         * `gen_ai.token.type` attribute.
         */
        public data class Type(public val type: TokenType) : Token {
            override val key: String = super.key.concatKey("type")
            override val value: String = type.str
        }

        /**
         * Allowed values for `gen_ai.token.type` per the OTel GenAI semantic conventions.
         */
        public enum class TokenType(public val str: String) {
            INPUT("input"),
            OUTPUT("output")
        }
    }

    /**
     * `gen_ai.usage` attribute namespace.
     */
    public sealed interface Usage : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("usage")

        /**
         * `gen_ai.usage.input_tokens` attribute.
         */
        public data class InputTokens(public val tokens: Int) : Usage {
            override val key: String = super.key.concatKey("input_tokens")
            override val value: Int = tokens
        }

        /**
         * `gen_ai.usage.output_tokens` attribute.
         */
        public data class OutputTokens(public val tokens: Int) : Usage {
            override val key: String = super.key.concatKey("output_tokens")
            override val value: Int = tokens
        }

        /**
         * `gen_ai.usage.total_tokens` attribute.
         */
        // Note: Non-semantic attribute
        public data class TotalTokens(public val tokens: Int) : Usage {
            override val key: String = super.key.concatKey("total_tokens")
            override val value: Int = tokens
        }
    }

    /**
     * `gen_ai.tool` attribute namespace.
     */
    public sealed interface Tool : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("tool")

        /**
         * `gen_ai.tool.call` attribute namespace.
         */
        public sealed interface Call : Tool {
            override val key: String
                get() = super.key.concatKey("call")

            /**
             * `gen_ai.tool.call.id` attribute.
             */
            public data class Id(public val id: String) : Call {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }

            /**
             * `gen_ai.tool.call.arguments` attribute.
             */
            public data class Arguments(public val arguments: JsonObject) : Call {
                override val key: String = super.key.concatKey("arguments")
                override val value: HiddenString = HiddenString(arguments.toString())
            }

            /**
             * `gen_ai.tool.call.result` attribute.
             */
            public data class Result(public val result: JsonElement) : Call {
                override val key: String = super.key.concatKey("result")
                override val value: HiddenString = HiddenString(result.toString())
            }
        }

        /**
         * `gen_ai.tool.description` attribute.
         */
        public data class Description(public val description: String) : Tool {
            override val key: String = super.key.concatKey("description")
            override val value: String = description
        }

        /**
         * `gen_ai.tool.name` attribute.
         */
        public data class Name(public val name: String) : Tool {
            override val key: String = super.key.concatKey("name")
            override val value: String = name
        }

        /**
         * `gen_ai.tool.definitions` attribute.
         */
        public data class Definitions(public val tools: List<ToolDescriptor>) : Tool {
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

    /**
     * `gen_ai.system_instructions` attribute.
     */
    public data class SystemInstructions(public val messages: List<Message.System>) : GenAIAttribute {
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

    /**
     * Encodes a list of [Message]s into the JSON-array string used by `gen_ai.input.messages` /
     * `gen_ai.output.messages`. Each entry has `role` and a `parts` array.
     */
    private fun List<Message>.toMessagesJsonString(): String =
        return buildJsonArray {
            forEach { message ->
                when (message) {
                    is Message.System,
                    is Message.Assistant -> {
                        add(
                            buildJsonObject {
                                put("role", JsonPrimitive(message.role.name))
                                putJsonArray("parts") {
                                    message.parts.forEach { part ->
                                        addMessagePart(part)
                                    }
                                }
                            }
                        )
                    }

                    is Message.User -> {
                        // Tool result must be added with tool role before user messages with text
                        add(
                            buildJsonObject {
                                put("role", JsonPrimitive("tool"))
                                putJsonArray("parts") {
                                    message.parts.filterIsInstance<MessagePart.Tool.Result>().forEach { part ->
                                        addMessagePart(part)
                                    }
                                }
                            }
                        )

                        add(
                            buildJsonObject {
                                put("role", JsonPrimitive(message.role.name))
                                putJsonArray("parts") {
                                    message.parts.filter { it !is MessagePart.Tool.Result }.forEach { part ->
                                        addMessagePart(part)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }.toString()
}

private fun JsonArrayBuilder.addMessagePart(part: MessagePart) {
    when (part) {
        is MessagePart.Text -> {
            addJsonObject {
                put("type", JsonPrimitive("text"))
                put("content", JsonPrimitive(part.text))
            }
        }

        is MessagePart.Reasoning -> {
            part.content.forEach {
                addJsonObject {
                    put("type", JsonPrimitive("reasoning"))
                    put("content", JsonPrimitive(it))
                }
            }
        }

        is MessagePart.Tool.Call -> {
            addJsonObject {
                put("type", JsonPrimitive("tool_call"))
                part.id?.let { id -> put("id", JsonPrimitive(id)) }
                put("name", JsonPrimitive(part.tool))
                put("arguments", part.argsJson)
            }
        }

        is MessagePart.Tool.Result -> {
            addJsonObject {
                put("type", JsonPrimitive("tool_call_response"))
                part.id?.let { id -> put("id", JsonPrimitive(id)) }
                put("result", JsonPrimitive(part.output))
            }
        }

        is MessagePart.Attachment -> {
            when (val source = part.source) {
                is AttachmentSource.Image -> {
                    addJsonObject {
                        put("type", JsonPrimitive("image"))
                        put("format", JsonPrimitive(source.format))
                        put("mimeType", JsonPrimitive(source.mimeType))
                        source.fileName?.let { name -> put("fileName", JsonPrimitive(name)) }
                    }
                }

                is AttachmentSource.Video -> {
                    addJsonObject {
                        put("type", JsonPrimitive("video"))
                        put("format", JsonPrimitive(source.format))
                        put("mimeType", JsonPrimitive(source.mimeType))
                        source.fileName?.let { name -> put("fileName", JsonPrimitive(name)) }
                    }
                }

                is AttachmentSource.Audio -> {
                    addJsonObject {
                        put("type", JsonPrimitive("audio"))
                        put("format", JsonPrimitive(source.format))
                        put("mimeType", JsonPrimitive(source.mimeType))
                        source.fileName?.let { name -> put("fileName", JsonPrimitive(name)) }
                    }
                }

                is AttachmentSource.File -> {
                    addJsonObject {
                        put("type", JsonPrimitive("file"))
                        put("format", JsonPrimitive(source.format))
                        put("mimeType", JsonPrimitive(source.mimeType))
                        source.fileName?.let { name -> put("fileName", JsonPrimitive(name)) }
                    }
                }
            }
        }
    }
}

//endregion Private Methods
