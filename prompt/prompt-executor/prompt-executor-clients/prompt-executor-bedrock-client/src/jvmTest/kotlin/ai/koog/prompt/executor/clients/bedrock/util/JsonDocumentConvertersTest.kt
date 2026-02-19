package ai.koog.prompt.executor.clients.bedrock.util

import aws.smithy.kotlin.runtime.content.Document
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonDocumentConvertersTest {

    @Test
    fun `test convertToDocument`() {
        /*
         kotlin.Number and therefore Document.Number doesn't have a proper equality check, so their asserts are
         customized and they are excluded from the composite checks
         */

        // Test null
        assertEquals(
            expected = null,
            actual = JsonDocumentConverters.convertToDocument(JsonNull)
        )

        // Test string primitive
        assertEquals(
            expected = Document.String("hello"),
            actual = JsonDocumentConverters.convertToDocument(JsonPrimitive("hello"))
        )

        // Test number primitive (integer)
        assertEquals(
            expected = Document.Number(42).value.toLong(),
            actual = (JsonDocumentConverters.convertToDocument(JsonPrimitive(42)) as Document.Number).value.toLong()
        )

        // Test number primitive (double)
        assertEquals(
            expected = Document.Number(3.14).value.toDouble(),
            actual = (JsonDocumentConverters.convertToDocument(JsonPrimitive(3.14)) as Document.Number).value.toDouble()
        )

        // Test boolean primitive
        assertEquals(
            expected = Document.Boolean(true),
            actual = JsonDocumentConverters.convertToDocument(JsonPrimitive(true))
        )

        // Test array
        assertEquals(
            expected = Document.List(
                listOf(
                    Document.String("item1"),
                    Document.Boolean(false),
                    null
                )
            ),
            actual = JsonDocumentConverters.convertToDocument(
                buildJsonArray {
                    add(JsonPrimitive("item1"))
                    add(JsonPrimitive(false))
                    add(JsonNull)
                }
            )
        )

        // Test object
        assertEquals(
            expected = Document.Map(
                mapOf(
                    "name" to Document.String("test"),
                    "active" to Document.Boolean(true),
                    "data" to null
                )
            ),
            actual = JsonDocumentConverters.convertToDocument(
                buildJsonObject {
                    put("name", JsonPrimitive("test"))
                    put("active", JsonPrimitive(true))
                    put("data", JsonNull)
                }
            )
        )
    }

    @Test
    fun `test convertToJsonElement`() {
        // Test null
        assertEquals(
            expected = JsonNull,
            actual = JsonDocumentConverters.convertToJsonElement(null)
        )

        // Test string
        assertEquals(
            expected = JsonPrimitive("hello"),
            actual = JsonDocumentConverters.convertToJsonElement(Document.String("hello"))
        )

        // Test number (integer)
        assertEquals(
            expected = JsonPrimitive(42),
            actual = JsonDocumentConverters.convertToJsonElement(Document.Number(42))
        )

        // Test number (double)
        assertEquals(
            expected = JsonPrimitive(3.14),
            actual = JsonDocumentConverters.convertToJsonElement(Document.Number(3.14))
        )

        // Test boolean
        assertEquals(
            expected = JsonPrimitive(true),
            actual = JsonDocumentConverters.convertToJsonElement(Document.Boolean(true))
        )

        // Test list
        assertEquals(
            expected = buildJsonArray {
                add(JsonPrimitive("item1"))
                add(JsonPrimitive(123))
                add(JsonPrimitive(false))
                add(JsonNull)
            },
            actual = JsonDocumentConverters.convertToJsonElement(
                Document.List(
                    listOf(
                        Document.String("item1"),
                        Document.Number(123),
                        Document.Boolean(false),
                        null
                    )
                )
            )
        )

        // Test map
        assertEquals(
            expected = buildJsonObject {
                put("name", JsonPrimitive("test"))
                put("count", JsonPrimitive(10))
                put("active", JsonPrimitive(true))
                put("data", JsonNull)
            },
            actual = JsonDocumentConverters.convertToJsonElement(
                Document.Map(
                    mapOf(
                        "name" to Document.String("test"),
                        "count" to Document.Number(10),
                        "active" to Document.Boolean(true),
                        "data" to null
                    )
                )
            )
        )
    }
}
