package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.utils.HiddenString
import kotlinx.serialization.json.JsonObject

internal object EventBodyFields {

    data class ToolCalls(
        private val tools: List<ai.koog.prompt.message.Message.Tool.Call>
    ) : EventBodyField() {
        override val key: String = "tool_calls"
        override val value: List<Map<String, Any>>
            get() {
                return tools.map { tool ->
                    buildMap {
                        val functionMap = buildMap {
                            put("name", HiddenString(tool.tool))
                            put("arguments", HiddenString(tool.content))
                        }

                        put("function", functionMap)
                        put("id", tool.id ?: "")
                        put("type", "function")
                    }
                }
            }
    }

    data class Arguments(private val arguments: JsonObject) : EventBodyField() {
        override val key: String = "arguments"
        override val value: HiddenString = HiddenString(arguments.toString())
    }

    data class Content(private val content: String) : EventBodyField() {
        override val key: String = "content"
        override val value: HiddenString = HiddenString(content)
    }

    data class Role(private val role: ai.koog.prompt.message.Message.Role) : EventBodyField() {
        override val key: String = "role"
        override val value: String = role.name.lowercase()
    }

    data class Index(private val index: Int) : EventBodyField() {
        override val key: String = "index"
        override val value: Int = index
    }

    data class FinishReason(private val reason: String) : EventBodyField() {
        override val key: String = "finish_reason"
        override val value: String = reason
    }

    data class Message(
        private val role: ai.koog.prompt.message.Message.Role,
        private val content: String
    ) : EventBodyField() {

        override val key: String = "message"
        override val value: Map<String, Any> = mapOf(
            "role" to role.name.lowercase(),
            "content" to HiddenString(content)
        )
    }

    data class Id(private val id: String) : EventBodyField() {
        override val key: String = "id"
        override val value: String = id
    }
}
