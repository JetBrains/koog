package ai.koog.prompt.executor.clients.bedrock.util

import aws.smithy.kotlin.runtime.content.Document
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Handles conversion between different free-form JSON formats:
 * [JsonElement] from kotlinx-serialization and [Document] from AWS SDK.
 */
internal object JsonDocumentConverters {
    fun convertToDocument(element: JsonElement): Document? = when (element) {
        is JsonNull -> null

        is JsonPrimitive -> run {
            // Number
            val number = element.longOrNull ?: element.doubleOrNull
            if (number != null) {
                return@run Document.Number(number)
            }

            // Boolean
            val boolean = element.booleanOrNull
            if (boolean != null) {
                return@run Document.Boolean(boolean)
            }

            // String - default
            return@run Document.String(element.content)
        }

        is JsonArray -> Document.List(element.map { convertToDocument(it) })

        is JsonObject -> Document.Map(element.mapValues { convertToDocument(it.value) })
    }

    fun convertToDocument(obj: JsonObject): Document =
        requireNotNull(convertToDocument(obj as JsonElement)) { "JsonObject can't convert to null Document" }

    fun convertToJsonElement(document: Document?): JsonElement = when (document) {
        null -> JsonNull
        is Document.Number -> JsonPrimitive(document.value)
        is Document.Boolean -> JsonPrimitive(document.value)
        is Document.String -> JsonPrimitive(document.value)
        is Document.List -> JsonArray(document.value.map { convertToJsonElement(it) })
        is Document.Map -> JsonObject(document.value.mapValues { convertToJsonElement(it.value) })
    }
}
