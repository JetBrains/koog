package ai.grazie.code.agents.core.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmInline

interface ToolResult {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
        }
    }

    fun toStringDefault(): String

    /**
     * Result implementation representing a simple tool result, just a string.
     */
    @kotlinx.serialization.Serializable
    @JvmInline
    value class Text(val text: String) : ToolResult.JSONSerializable<Text> {
        override val serializer: KSerializer<Text>
            get() = serializer()

        constructor(e: Exception) : this("Failed with exception '${e::class.simpleName}' and message '${e.message}'")

        companion object {
            inline fun build(block: StringBuilder.() -> Unit): Text = Text(StringBuilder().apply(block).toString())
        }

        override fun toStringDefault(): String = text
    }

    @JvmInline
    value class Boolean(val result: kotlin.Boolean) : ToolResult {
        companion object {
            val TRUE = Boolean(true)
            val FALSE = Boolean(false)
        }

        override fun toStringDefault(): String = result.toString()
    }

    interface JSONSerializable<T : JSONSerializable<T>> : ToolResult {
        val serializer: KSerializer<T>

        override fun toStringDefault(): String = json.encodeToString(serializer, this as T)
    }
}