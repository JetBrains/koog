package ai.koog.agents.features.opentelemetry.event

import kotlinx.serialization.json.JsonObject

internal object EventBodyFields {

    data class ToolCalls(
        private val tools: List<ai.koog.prompt.message.Message.Tool>
    ) : EventBodyField() {
        override val key: String = "tool_calls"
        override val value: List<Map<String, Any>>
            get() {
                return tools.map { tool ->
                    buildMap {
                        val functionMap = buildMap {
                            put("name", tool.tool)
                            put("arguments", tool.content)
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
        override val value: String = arguments.toString()
    }

    data class Content(private val content: String) : EventBodyField() {
        override val key: String = "content"
        override val value: String = content
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
        private val role: ai.koog.prompt.message.Message.Role?,
        private val content: String
    ) : EventBodyField() {

        override val key: String = "message"
        override val value: Map<String, String> = buildMap {
            role?.let { role -> put("role", role.name.lowercase()) }
            put("content", content)
        }
    }

    data class Id(private val id: String) : EventBodyField() {
        override val key: String = "id"
        override val value: String = id
    }
}
