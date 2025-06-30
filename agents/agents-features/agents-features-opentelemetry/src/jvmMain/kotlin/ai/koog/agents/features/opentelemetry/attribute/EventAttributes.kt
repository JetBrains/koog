package ai.koog.agents.features.opentelemetry.attribute

import ai.koog.prompt.message.Message

internal object EventAttributes {

    sealed interface Body : Attribute {

        data class ToolCalls(
            private val tools: List<Message.Tool>,
            override val verbose: Boolean = false
        ) : Body {
            override val key: String = "tool_calls"
            override val value: List<Map<String, Any>>
                get() {
                    return tools.map { tool ->
                        buildMap {
                            val functionMap = buildMap {
                                put("name", tool.tool)
                                if (verbose) {
                                    put("arguments", tool.content)
                                }
                            }

                            put("function", functionMap)
                            put("id", tool.id ?: "")
                            put("type", "function")
                        }
                    }
                }
        }

        data class Content(private val content: String) : Body {
            override val key: String = "content"
            override val value: String = content
        }

        data class Role(private val role: Message.Role) : Body {
            override val key: String = "role"
            override val value: String = role.name.lowercase()
        }

        data class Index(private val index: Int) : Body {
            override val key: String = "index"
            override val value: Int = index
        }

        data class FinishReason(private val reason: String) : Body {
            override val key: String = "finish_reason"
            override val value: String = reason
        }

        data class Message(private val role: Message.Role?, private val content: String) : Body {
            override val key: String = "message"
            override val value: Map<String, String> = buildMap {
                role?.let { role -> put("role", role.name.lowercase()) }
                put("content", content)
            }
        }

        data class Id(private val id: String) : Body {
            override val key: String = "id"
            override val value: String = id
        }
    }

}
