package ai.koog.serialization

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe

/**
 * Abstract test suite for [JSONSerializer] implementations.
 * Provides detailed [JSONElement] serialization test scenarios.
 */
abstract class JSONElementSerializationTestBase {
    abstract val serializer: JSONSerializer

    open fun testJSONNull() {
        val element = JSONNull
        //language=JSON
        val jsonString = "null"

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe element
    }

    open fun testJSONLiteralString() {
        val element = JSONPrimitive("hello")
        //language=JSON
        val jsonString = "\"hello\""

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe element
    }

    open fun testJSONLiteralNumber() {
        val element = JSONPrimitive(42)
        //language=JSON
        val jsonString = "42"

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe JSONLiteral("42", isString = false)
    }

    open fun testJSONLiteralBoolean() {
        val element = JSONPrimitive(true)
        //language=JSON
        val jsonString = "true"

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe JSONLiteral("true", isString = false)
    }

    open fun testJSONPrimitiveString() {
        val element = JSONPrimitive("world")
        //language=JSON
        val jsonString = "\"world\""

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe element
    }

    open fun testJSONPrimitiveNull() {
        val element: JSONPrimitive = JSONNull
        //language=JSON
        val jsonString = "null"

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe element
    }

    open fun testJSONArrayEmpty() {
        val element = JSONArray(emptyList())
        //language=JSON
        val jsonString = "[]"

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe element
    }

    open fun testJSONArrayWithPrimitives() {
        val element = JSONArray(
            listOf(
                JSONPrimitive(1),
                JSONPrimitive("test"),
                JSONPrimitive(true),
                JSONNull
            )
        )
        //language=JSON
        val jsonString = """
            [1, "test", true, null]
        """

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe JSONArray(
            listOf(
                JSONLiteral("1", isString = false),
                JSONLiteral("test", isString = true),
                JSONLiteral("true", isString = false),
                JSONNull
            )
        )
    }

    open fun testJSONObjectEmpty() {
        val element = JSONObject(emptyMap())
        //language=JSON
        val jsonString = "{}"

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe element
    }

    open fun testJSONObjectWithPrimitives() {
        val element = JSONObject(
            mapOf(
                "name" to JSONPrimitive("John"),
                "age" to JSONPrimitive(30),
                "active" to JSONPrimitive(true),
                "data" to JSONNull
            )
        )
        //language=JSON
        val jsonString = """
            {
              "name": "John",
              "age": 30,
              "active": true,
              "data": null
            }
        """

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe JSONObject(
            mapOf(
                "name" to JSONLiteral("John", isString = true),
                "age" to JSONLiteral("30", isString = false),
                "active" to JSONLiteral("true", isString = false),
                "data" to JSONNull
            )
        )
    }

    open fun testJSONElementNestedStructure() {
        val element = JSONObject(
            mapOf(
                "user" to JSONObject(
                    mapOf(
                        "name" to JSONPrimitive("Alice"),
                        "scores" to JSONArray(
                            listOf(
                                JSONPrimitive(95),
                                JSONPrimitive(87),
                                JSONPrimitive(92)
                            )
                        )
                    )
                ),
                "metadata" to JSONObject(
                    mapOf(
                        "version" to JSONPrimitive(1),
                        "nullable" to JSONNull
                    )
                )
            )
        )
        //language=JSON
        val jsonString = """
            {
              "user": {
                "name": "Alice",
                "scores": [95, 87, 92]
              },
              "metadata": {
                "version": 1,
                "nullable": null
              }
            }
        """

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe JSONObject(
            mapOf(
                "user" to JSONObject(
                    mapOf(
                        "name" to JSONLiteral("Alice", isString = true),
                        "scores" to JSONArray(
                            listOf(
                                JSONLiteral("95", isString = false),
                                JSONLiteral("87", isString = false),
                                JSONLiteral("92", isString = false)
                            )
                        )
                    )
                ),
                "metadata" to JSONObject(
                    mapOf(
                        "version" to JSONLiteral("1", isString = false),
                        "nullable" to JSONNull
                    )
                )
            )
        )
    }

    open fun testJSONElementArray() {
        val element = JSONArray(
            listOf(
                JSONObject(mapOf("id" to JSONPrimitive(1))),
                JSONObject(mapOf("id" to JSONPrimitive(2))),
                JSONObject(mapOf("id" to JSONPrimitive(3)))
            )
        )
        //language=JSON
        val jsonString = """
            [
              {"id": 1},
              {"id": 2},
              {"id": 3}
            ]
        """

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe JSONArray(
            listOf(
                JSONObject(mapOf("id" to JSONLiteral("1", isString = false))),
                JSONObject(mapOf("id" to JSONLiteral("2", isString = false))),
                JSONObject(mapOf("id" to JSONLiteral("3", isString = false)))
            )
        )
    }

    open fun testJSONUnquotedPrimitive() {
        val element = JSONUnquotedPrimitive("12345678901234567890")
        //language=JSON
        val jsonString = "12345678901234567890"

        serializer.encodeJSONElementToString(element) shouldEqualJson jsonString
        serializer.decodeJSONElementFromString(jsonString) shouldBe JSONLiteral("12345678901234567890", isString = false)
    }

    open fun testRoundTripSerialization() {
        val original: JSONElement = JSONObject(
            mapOf(
                "string" to JSONPrimitive("value"),
                "number" to JSONPrimitive(42.5),
                "boolean" to JSONPrimitive(false),
                "null" to JSONNull,
                "array" to JSONArray(listOf(JSONPrimitive(1), JSONPrimitive(2))),
                "nested" to JSONObject(mapOf("key" to JSONPrimitive("nested value")))
            )
        )

        val serialized = serializer.encodeJSONElementToString(original)
        val deserialized = serializer.decodeJSONElementFromString(serialized)

        deserialized shouldBe original
    }
}
