package ai.koog.agents.core.utils

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.ModeratedMessage
import ai.koog.agents.core.dsl.extension.TestLLMExecutor.Companion.testClock
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.js.JsName
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SerializationUtilsTest {

    @Serializable
    private data class TestData(val name: String, val value: Int)

    private val json = Json {
        prettyPrint = true
    }

    //region encodeDataToStringOrNull

    @Test
    @JsName("encodeDataToStringOrNullShouldSerializeValidData")
    fun `encodeDataToStringOrNull should serialize valid data`() {
        val data = TestData("test", 42)

        val actualString =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToStringOrNull(data, typeOf<TestData>())

        val expectedJsonElement = JsonObject(
            mapOf(
                "name" to JsonPrimitive("test"),
                "value" to JsonPrimitive(42)
            )
        )

        val expectedString = json.encodeToString(JsonElement.serializer(), expectedJsonElement)

        assertNotNull(actualString)
        assertEquals(expectedString, actualString)
    }

    @Test
    @JsName("encodeDataToStringOrNullShouldReturnNullForNonSerializableData")
    fun `encodeDataToStringOrNull should return null for non-serializable data`() {
        val testName = "test-name"

        @Suppress("unused")
        val data = object {
            val name = testName
        }

        val actualString =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToStringOrNull(data, typeOf<Any>())

        assertNull(actualString)
    }

    @Test
    @JsName("encodeDataToStringOrNullForTypesWithoutSerializer")
    fun `encodeDataToStringOrNull for types without serializer`() {
        class TestClassWithoutSerializer(val name: String)

        val testObjectWithoutSerializer = TestClassWithoutSerializer("test")

        @OptIn(InternalAgentsApi::class)
        val serializedData = SerializationUtils.encodeDataToStringOrNull(
            data = testObjectWithoutSerializer,
            dataType = typeOf<TestClassWithoutSerializer>()
        )

        assertNull(serializedData)
    }

    @Test
    @JsName("encodeDataToStringOrNullForTypesWithStarAndNoSerializer")
    fun `encodeDataToStringOrNull for types with star and no serializer`() {
        val testObjectWithStar: List<*> = listOf("test", 1)

        @OptIn(InternalAgentsApi::class)
        val serializedData = SerializationUtils.encodeDataToStringOrNull(
            data = testObjectWithStar,
            dataType = typeOf<List<*>>()
        )

        assertNull(serializedData)
    }

    //endregion encodeDataToStringOrNull

    //region encodeDataToJsonElementOrNull

    @Test
    @JsName("encodeDataToJsonElementOrNullShouldSerializeValidData")
    fun `encodeDataToJsonElementOrNull should serialize valid data`() {
        val testName = "test-name"
        val testValue = 42
        val data = TestData(testName, testValue)

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToJsonElementOrNull(data, typeOf<TestData>())

        val expectedJsonElement = JsonObject(
            mapOf(
                "name" to JsonPrimitive(testName),
                "value" to JsonPrimitive(testValue)
            )
        )

        assertNotNull(actualJsonElement)
        assertEquals(expectedJsonElement, actualJsonElement)
    }

    @Test
    @JsName("encodeDataToJsonElementOrNullShouldReturnNullForNonSerializableData")
    fun `encodeDataToJsonElementOrNull should return null for non-serializable data`() {
        val testName = "test-name"

        @Suppress("unused")
        val data = object {
            val name = testName
        }

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToJsonElementOrNull(data, typeOf<Any>())

        assertNull(actualJsonElement)
    }

    @Test
    @JsName("encodeDataToJsonElementOrNullForTypesWithoutSerializer")
    fun `encodeDataToJsonElementOrNull for types without serializer`() {
        class TestClassWithoutSerializer(val name: String)

        val testObjectWithoutSerializer = TestClassWithoutSerializer("test")

        @OptIn(InternalAgentsApi::class)
        val serializedData = SerializationUtils.encodeDataToJsonElementOrNull(
            data = testObjectWithoutSerializer,
            dataType = typeOf<TestClassWithoutSerializer>()
        )

        assertNull(serializedData)
    }

    @Test
    @JsName("encodeDataToJsonElementOrNullForTypesWithStarAndNoSerializer")
    fun `encodeDataToJsonElementOrNull for types with star and no serializer`() {
        val testObjectWithStar: List<*> = listOf("test", 1)

        @OptIn(InternalAgentsApi::class)
        val serializedData = SerializationUtils.encodeDataToJsonElementOrNull(
            data = testObjectWithStar,
            dataType = typeOf<List<*>>()
        )

        assertNull(serializedData)
    }

    //endregion encodeDataToJsonElementOrNull

    //region encodeDataToString

    @Test
    @JsName("encodeDataToStringShouldSerializeValidData")
    fun `encodeDataToString should serialize valid data`() {
        val testName = "test-name"
        val testValue = 42
        val data = TestData(testName, testValue)

        val actualString =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToString(data, typeOf<TestData>())

        val expectedJsonElement = JsonObject(
            mapOf(
                "name" to JsonPrimitive(testName),
                "value" to JsonPrimitive(testValue)
            )
        )

        val expectedString = json.encodeToString(JsonElement.serializer(), expectedJsonElement)

        assertNotNull(actualString)
        assertEquals(expectedString, actualString)
    }

    @Test
    @JsName("encodeDataToStringShouldThrowExceptionWhenNoSerializerFound")
    fun `encodeDataToString should throw exception when no serializer found`() {
        val testName = "test-name"

        @Suppress("unused")
        val data = object {
            val name = testName
        }

        val throwable = assertFailsWith<SerializationException> {
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToString(data, typeOf<Any>())
        }

        val actualMessage = throwable.message
        assertNotNull(actualMessage)
        assertTrue(actualMessage.contains("Serializer for class '${Any::class.simpleName}' is not found."))
    }

    @Test
    @JsName("encodeDataToStringForTypesWithoutSerializer")
    fun `encodeDataToString for types without serializer`() {
        class TestClassWithoutSerializer(val name: String)

        val testObjectWithoutSerializer = TestClassWithoutSerializer("test")

        @OptIn(InternalAgentsApi::class)
        val throwable = assertFailsWith<SerializationException> {
            SerializationUtils.encodeDataToString(
                data = testObjectWithoutSerializer,
                dataType = typeOf<TestClassWithoutSerializer>()
            )
        }

        val actualMessage = throwable.message
        assertNotNull(actualMessage)
        assertTrue(
            actualMessage.startsWith(
                "Serializer for class '${TestClassWithoutSerializer::class.simpleName}' is not found.\n" +
                    "Please ensure that class is marked as '@Serializable' and that the serialization compiler plugin is applied."
            )
        )
    }

    @Test
    @JsName("encodeDataToStringForTypesWithStarAndNoSerializer")
    fun `encodeDataToString for types with star and no serializer`() {
        val testObjectWithStar: List<*> = listOf("test", 1)

        @OptIn(InternalAgentsApi::class)
        val throwable = assertFailsWith<IllegalArgumentException> {
            SerializationUtils.encodeDataToString(
                data = testObjectWithStar,
                dataType = typeOf<List<*>>()
            )
        }

        assertEquals(
            "Star projections in type arguments are not allowed, but had null",
            throwable.message
        )
    }

    @Test
    @JsName("encodeDataToStringForModeratedMessageType")
    fun `encodeDataToString for ModeratedMessage type`() {
        val message = Message.User(
            parts = listOf(
                ContentPart.Text("Test message")
            ),
            metaInfo = RequestMetaInfo(timestamp = testClock.now())
        )

        val moderationResult = ModerationResult(
            isHarmful = false,
            categories = mapOf(
                ModerationCategory.Harassment to ModerationCategoryResult(
                    detected = false,
                    confidenceScore = 0.0,
                    appliedInputTypes = listOf(ModerationResult.InputType.TEXT)
                ),
                ModerationCategory.ViolenceGraphic to ModerationCategoryResult(
                    detected = false,
                    confidenceScore = 0.0,
                    appliedInputTypes = listOf(ModerationResult.InputType.IMAGE)
                ),
            )
        )

        val moderatedMessage = ModeratedMessage(
            message = message,
            moderationResult = moderationResult
        )

        @OptIn(InternalAgentsApi::class)
        val serializedString = SerializationUtils.encodeDataToString(
            data = moderatedMessage,
            dataType = typeOf<ModeratedMessage>()
        )

        assertNotNull(serializedString)
    }

    //endregion encodeDataToString

    //region encodeDataToJsonElement

    @Test
    @JsName("encodeDataToJsonElementShouldSerializeValidData")
    fun `encodeDataToJsonElement should serialize valid data`() {
        val testName = "test-name"
        val testValue = 42
        val data = TestData(testName, testValue)

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToJsonElement(data, typeOf<TestData>())

        val expectedJsonElement = JsonObject(
            mapOf(
                "name" to JsonPrimitive(testName),
                "value" to JsonPrimitive(testValue)
            )
        )

        assertNotNull(actualJsonElement)
        assertEquals(expectedJsonElement, actualJsonElement)
    }

    @Test
    @JsName("encodeDataToJsonElementShouldThrowExceptionWhenNoSerializerFound")
    fun `encodeDataToJsonElement should throw exception when no serializer found`() {
        val testName = "test-name"

        @Suppress("unused")
        val data = object {
            val name = testName
        }

        val throwable = assertFailsWith<SerializationException> {
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToJsonElement(data, typeOf<Any>())
        }

        val actualMessage = throwable.message
        assertNotNull(actualMessage)
        assertTrue(actualMessage.contains("Serializer for class '${Any::class.simpleName}' is not found."))
    }

    @Test
    @JsName("encodeDataToJsonElementForTypesWithoutSerializer")
    fun `encodeDataToJsonElement for types without serializer`() {
        class TestClassWithoutSerializer(val name: String)

        val testObjectWithoutSerializer = TestClassWithoutSerializer("test")

        @OptIn(InternalAgentsApi::class)
        val throwable = assertFailsWith<SerializationException> {
            SerializationUtils.encodeDataToJsonElement(
                data = testObjectWithoutSerializer,
                dataType = typeOf<TestClassWithoutSerializer>()
            )
        }

        val actualMessage = throwable.message
        assertNotNull(actualMessage)
        assertTrue(
            actualMessage.startsWith(
                "Serializer for class '${TestClassWithoutSerializer::class.simpleName}' is not found.\n" +
                    "Please ensure that class is marked as '@Serializable' and that the serialization compiler plugin is applied"
            )
        )
    }

    @Test
    @JsName("encodeDataToJsonElementForTypesWithStarAndNoSerializer")
    fun `encodeDataToJsonElement for types with star and no serializer`() {
        val testObjectWithStar: List<*> = listOf("test", 1)

        @OptIn(InternalAgentsApi::class)
        val throwable = assertFailsWith<IllegalArgumentException> {
            SerializationUtils.encodeDataToJsonElement(
                data = testObjectWithStar,
                dataType = typeOf<List<*>>()
            )
        }

        assertEquals(
            "Star projections in type arguments are not allowed, but had null",
            throwable.message
        )
    }

    @Test
    @JsName("encodeDataToJsonElementForModeratedMessageType")
    fun `encodeDataToJsonElement for ModeratedMessage type`() {
        val message = Message.User(
            parts = listOf(
                ContentPart.Text("Test message")
            ),
            metaInfo = RequestMetaInfo(timestamp = testClock.now())
        )

        val moderationResult = ModerationResult(
            isHarmful = false,
            categories = mapOf(
                ModerationCategory.Harassment to ModerationCategoryResult(
                    detected = false,
                    confidenceScore = 0.0,
                    appliedInputTypes = listOf(ModerationResult.InputType.TEXT)
                ),
                ModerationCategory.ViolenceGraphic to ModerationCategoryResult(
                    detected = false,
                    confidenceScore = 0.0,
                    appliedInputTypes = listOf(ModerationResult.InputType.IMAGE)
                ),
            )
        )

        val moderatedMessage = ModeratedMessage(
            message = message,
            moderationResult = moderationResult
        )

        @OptIn(InternalAgentsApi::class)
        val serializedString = SerializationUtils.encodeDataToJsonElement(
            data = moderatedMessage,
            dataType = typeOf<ModeratedMessage>()
        )

        assertNotNull(serializedString)
    }

    //endregion encodeDataToJsonElement

    //region parseDataToJsonElementOrDefault

    @Test
    @JsName("parseDataToJsonElementOrDefaultShouldParseValidJsonString")
    fun `parseDataToJsonElementOrDefault should parse valid JSON string`() {
        val testName = "test-name"
        val testValue = 42
        val jsonString = """{"name":"$testName","value":$testValue}"""

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.parseDataToJsonElementOrDefault(jsonString)

        val actualJsonObject = actualJsonElement as? JsonObject
        assertNotNull(actualJsonObject)

        assertEquals(JsonPrimitive(testName), actualJsonObject["name"])
        assertEquals(JsonPrimitive(testValue), actualJsonObject["value"])
    }

    @Test
    @JsName("parseDataToJsonElementOrDefaultShouldReturnJsonPrimitiveForInvalidJson")
    fun `parseDataToJsonElementOrDefault should return JsonPrimitive for invalid JSON`() {
        val invalidJson = "not a valid json"

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.parseDataToJsonElementOrDefault(invalidJson)

        assertTrue(actualJsonElement is JsonPrimitive)
        assertEquals(JsonPrimitive(invalidJson), actualJsonElement)
    }

    @Test
    @JsName("parseDataToJsonElementOrDefaultShouldParseJsonArray")
    fun `parseDataToJsonElementOrDefault should parse JSON array`() {
        val jsonArray = """[1, 2, 3]"""

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.parseDataToJsonElementOrDefault(jsonArray)

        val expectedJsonElement = JsonArray(
            listOf(
                JsonPrimitive(1),
                JsonPrimitive(2),
                JsonPrimitive(3)
            )
        )

        assertNotNull(actualJsonElement)
        assertEquals(expectedJsonElement, actualJsonElement)
    }

    @Test
    @JsName("parseDataToJsonElementOrDefaultShouldParseJsonPrimitive")
    fun `parseDataToJsonElementOrDefault should parse JSON primitive`() {
        val jsonPrimitive = "\"simple string\""

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.parseDataToJsonElementOrDefault(jsonPrimitive)

        assertTrue(actualJsonElement is JsonPrimitive)
        assertEquals(JsonPrimitive("simple string"), actualJsonElement)
    }

    @Test
    @JsName("parseDataToJsonElementOrDefaultShouldUseProvidedDefaultForInvalidJson")
    fun `parseDataToJsonElementOrDefault should use provided default for invalid JSON`() {
        val invalidJson = "this is not json"
        val defaultElement = JsonObject(mapOf("default" to JsonPrimitive("used")))

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.parseDataToJsonElementOrDefault(invalidJson) { defaultElement }

        assertEquals(defaultElement, actualJsonElement)
    }

    //endregion parseDataToJsonElementOrDefault

    //region encodeDataToStringOrDefault

    @Test
    @JsName("encodeDataToStringOrDefaultShouldSerializeValidData")
    fun `encodeDataToStringOrDefault should serialize valid data`() {
        val data = TestData("test", 42)

        val actualString =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToStringOrDefault(data, typeOf<TestData>())

        val expectedJsonElement = JsonObject(
            mapOf(
                "name" to JsonPrimitive("test"),
                "value" to JsonPrimitive(42)
            )
        )

        val expectedString = json.encodeToString(JsonElement.serializer(), expectedJsonElement)

        assertEquals(expectedString, actualString)
    }

    @Test
    @JsName("encodeDataToStringOrDefaultShouldReturnProvidedDefaultForNonSerializableData")
    fun `encodeDataToStringOrDefault should return provided default for non-serializable data`() {
        val testName = "test-name"
        val defaultValue = "default-string"

        @Suppress("unused")
        val data = object {
            val name = testName
        }

        val actualString =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToStringOrDefault(data, typeOf<Any>()) { defaultValue }

        assertEquals(defaultValue, actualString)
    }

    //endregion encodeDataToStringOrDefault

    //region encodeDataToJsonElementOrDefault

    @Test
    @JsName("encodeDataToJsonElementOrDefaultShouldSerializeValidData")
    fun `encodeDataToJsonElementOrDefault should serialize valid data`() {
        val testName = "test-name"
        val testValue = 42

        val data = TestData(testName, testValue)

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToJsonElementOrDefault(data, typeOf<TestData>())

        val expectedJsonElement = JsonObject(
            mapOf(
                "name" to JsonPrimitive(testName),
                "value" to JsonPrimitive(testValue)
            )
        )

        assertEquals(expectedJsonElement, actualJsonElement)
    }

    @Test
    @JsName("encodeDataToJsonElementOrDefaultShouldReturnProvidedDefaultForNonSerializableData")
    fun `encodeDataToJsonElementOrDefault should return provided default for non-serializable data`() {
        val testName = "test-name"
        val defaultElement = JsonObject(
            mapOf(
                "fallback" to JsonPrimitive(true)
            )
        )

        @Suppress("unused")
        val data = object {
            val name = testName
        }

        val actualJsonElement =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToJsonElementOrDefault(data, typeOf<Any>()) { defaultElement }

        assertEquals(defaultElement, actualJsonElement)
    }

    //endregion encodeDataToJsonElementOrDefault
}
