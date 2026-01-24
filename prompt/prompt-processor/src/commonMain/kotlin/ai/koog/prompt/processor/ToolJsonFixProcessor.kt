package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer

/**
 * Configuration for parsing and fixing tool call json
 *
 * @property json The json parser to use for parsing tool call json
 * @property idJsonKeys The keys used by various models for tool ID in tool call json
 * @property nameJsonKeys The keys used by various models for tool name in tool call json
 * @property argsJsonKeys The keys used by various models for tool arguments in tool call json
 */
public class ToolCallJsonConfig(
    public val json: Json = defaultJson,
    public val idJsonKeys: List<String> = defaultIdJsonKeys,
    public val nameJsonKeys: List<String> = defaultNameJsonKeys,
    public val argsJsonKeys: List<String> = defaultArgsJsonKeys,
) {

    /**
     * Companion object with defaults for json configurations of [ToolCallJsonConfig]
     */
    public companion object {

        /**
         * Default json configuration used to parse tool call json
         */
        public val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Keys used by various models for tool ID in tool call json
         */
        public val defaultIdJsonKeys: List<String> = listOf("id", "tool_call_id")

        /**
         * Keys used by various models for tool name in tool call json
         */
        public val defaultNameJsonKeys: List<String> = listOf("name", "tool", "tool_name")

        /**
         * Keys used by various models for tool arguments in tool call json
         */
        public val defaultArgsJsonKeys: List<String> = listOf("arguments", "args", "parameters", "params", "tool_args")
    }
}

/**
 * Abstract class for processors that fix tool call json messages.
 * Contains common logic for extracting relevant information from (possibly malformed) tool call json messages.
 */
public abstract class ToolJsonFixProcessor(
    protected val toolRegistry: ToolRegistry,
    protected val toolCallJsonConfig: ToolCallJsonConfig = ToolCallJsonConfig()
) : ResponseProcessor() {

    /**
     * Returns a regex pattern that matches any of the given keys.
     */
    protected fun getKeyPattern(keys: List<String>): String {
        return """"(${keys.joinToString("|")})""""
    }

    /**
     * Fixes a malformed json string by escaping special characters and removing incorrect escapes.
     */
    protected fun fixJsonString(jsonValue: String): String {
        // Remove the surrounding quotes to work with the content
        val content = if (jsonValue.startsWith('"') && jsonValue.endsWith('"')) {
            jsonValue.drop(1).dropLast(1)
        } else {
            jsonValue
        }

        val correctString = StringBuilder()
        var i = 0
        while (i < content.length) {
            if (content[i] == '\\' && i + 1 < content.length) {
                when (content[i + 1]) {
                    '"' -> '"'
                    '\\' -> '\\'
                    '/' -> '/'
                    'b' -> '\b'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    else -> null
                }?.let {
                    correctString.append(it)
                    i++
                }
            } else {
                // Regular character
                correctString.append(content[i])
            }
            i++
        }

        return correctString.toString()
    }

    /**
     * Extracts tool name from a json-like tool call message.
     * Uses regex to parse the tool name even if the message is malformed.
     */
    protected fun getToolName(messageContent: String): String? {
        val toolKeyPattern = getKeyPattern(toolCallJsonConfig.nameJsonKeys)
        val toolNameRegex = """$toolKeyPattern\s*:\s*"([a-zA-Z0-9_]+)"""".toRegex()
        return toolNameRegex.find(messageContent)?.groupValues?.get(2)
    }

    /**
     * Uses heuristics to extract a tool call from a json-like tool call message.
     * If the message is malformed, regex-based parsing is used to extract tool name and arguments.
     *
     * @return the extracted tool call or null if the extraction failed.
     */
    protected fun extractToolCall(
        messageContent: String,
        metaInfo: ResponseMetaInfo,
    ): Message.Tool.Call? {
        runCatching {
            val decodedToolCall = toolCallJsonConfig.json.decodeFromString(toolCallDeserializer, messageContent)
            return Message.Tool.Call(
                decodedToolCall.id,
                decodedToolCall.tool,
                decodedToolCall.arguments.toString(),
                metaInfo
            )
        }

        val toolName = getToolName(messageContent) ?: return null

        val params = runCatching {
            toolRegistry.getTool(toolName).descriptor.requiredParameters
        }.getOrNull() ?: return null

        val argsKeyPattern = getKeyPattern(toolCallJsonConfig.argsJsonKeys)
        val argsPattern = """\{\s*${params.joinToString("\\s*,\\s*") { """"${it.name}"\s*:\s*(.+)""" }}\s*\}\s*\}"""
        val argsRegex = """$argsKeyPattern\s*:\s*$argsPattern""".toRegex()
        val argsMatch = argsRegex.find(messageContent)?.groupValues
        val args = argsMatch?.drop(2) ?: return null

        val fixedArgs = buildJsonObject {
            params.zip(args).forEach { (param, argValue) ->
                val key = param.name
                val value = when (param.type) {
                    is ToolParameterType.String -> JsonPrimitive(fixJsonString(argValue))
                    else -> toolCallJsonConfig.json.parseToJsonElement(argValue)
                }
                put(key, value)
            }
        }.toString()

        val idKeyPattern = getKeyPattern(toolCallJsonConfig.idJsonKeys)
        val idRegex = """$idKeyPattern\s*:\s*"([a-zA-Z0-9_]+)"""".toRegex()
        val id = idRegex.find(messageContent)?.groupValues?.get(3)

        return Message.Tool.Call(id, toolName, fixedArgs, metaInfo)
    }

    /**
     * Represents a tool call message in a json-like format.
     */
    @Serializable
    protected data class ToolCall(
        val id: String? = null,
        val tool: String,
        val arguments: JsonObject
    )

    /**
     * Deserializer for [ToolCall] messages assuming varying json formats.
     */
    protected val toolCallDeserializer: DeserializationStrategy<ToolCall>
        get() = object : DeserializationStrategy<ToolCall> {
            private val baseSerializer = serializer<ToolCall>()
            override val descriptor = baseSerializer.descriptor

            override fun deserialize(decoder: Decoder): ToolCall {
                require(decoder is JsonDecoder) { "This serializer can only be used with JSON" }

                val jsonElement = decoder.decodeJsonElement()
                require(jsonElement is JsonObject) { "Expected a JSON object" }

                return deserializeFromJsonObject(jsonElement, decoder.json)
            }

            private fun deserializeFromJsonObject(jsonObject: JsonObject, json: Json): ToolCall {
                var objectToDeserialize = findNestedObject(jsonObject) ?: jsonObject

                objectToDeserialize = updateKey(objectToDeserialize, toolCallJsonConfig.idJsonKeys, "id")
                objectToDeserialize = updateKey(objectToDeserialize, toolCallJsonConfig.nameJsonKeys, "tool")
                objectToDeserialize = updateKey(objectToDeserialize, toolCallJsonConfig.argsJsonKeys, "arguments")

                return json.decodeFromJsonElement(baseSerializer, objectToDeserialize)
            }

            private fun findNestedObject(jsonObject: JsonObject): JsonObject? =
                jsonObject.takeIf { it.size == 1 }?.entries?.first()?.value as? JsonObject

            private fun updateKey(
                jsonObject: JsonObject,
                expectedKeys: List<String>,
                updatedKey: String
            ) = buildJsonObject {
                for ((key, value) in jsonObject) {
                    put(if (key in expectedKeys) updatedKey else key, value)
                }
            }
        }
}
