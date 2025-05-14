package ai.grazie.code.prompt.structure.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

public class JsonStructureLanguage(
    private val json: Json = defaultJson
) {
    public companion object {
        public val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            isLenient = true
            coerceInputValues = true
            classDiscriminator = "kind"
        }
    }

    private val jsonPretty = Json(json) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    public fun <T> string(value: T, serializer: KSerializer<T>): String = json.encodeToString(serializer, value)
    public inline fun <reified T> string(value: T): String = string(value, serializer<T>())

    public fun <T> pretty(value: T, serializer: KSerializer<T>): String = jsonPretty.encodeToString(serializer, value)

    public inline fun <reified T> pretty(value: T): String = pretty(
        value,
        serializer<T>()
    )

    public fun <T> parse(text: String, serializer: KSerializer<T>): T = json.decodeFromString(serializer, cleanup(text))

    private fun cleanup(text: String): String {
        //cleanup some lines that are not json
        var lines = text.lines().map { it.trim() }
        lines = lines.filter { it.isNotBlank() }
        lines = lines.filter {
            val start = it.firstOrNull() ?: return@filter false
            val isStructureStart = start in setOf('{', '[', '"', '}', ']', '\'')
            val isDigit = start.isDigit() || start == '-'
            val isPrimitive = it.startsWith("true") || it.startsWith("false") || it.startsWith("null")
            isStructureStart || isDigit || isPrimitive
        }
        val content = lines.joinToString("\n")
        return content
    }

    public inline fun <reified T> parse(text: String): T = parse(text, serializer<T>())
}
