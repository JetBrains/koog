@file:Suppress("unused", "UNUSED_PARAMETER")

package ai.koog.agents.core.tools.schema

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.serialization.KotlinClassToken
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * kotlinx.serialization.json built-in types (JsonObject, JsonElement, ...) as tool parameters, see KG-822 / #1921.
 */
@OptIn(InternalAgentToolsApi::class)
class JsonBuiltinTypesSchemaTest {
    private val freeFormObject = ToolParameterType.Object(
        properties = emptyList(),
        requiredProperties = emptyList(),
        additionalProperties = true,
        additionalPropertiesType = null,
    )

    private val json = Json { encodeDefaults = false }

    @Serializable
    data class PostArgs(
        @property:LLMDescription("Target URL")
        val url: String,
        @property:LLMDescription("Request body")
        val payload: JsonObject,
        val headers: Map<String, String> = emptyMap(),
        val metadata: JsonObject? = null,
    )

    @Serializable
    data class CollectionArgs(
        val records: List<JsonObject>,
        val byId: Map<String, JsonObject>,
    )

    @Serializable
    data class Node(
        val data: JsonObject,
        val children: List<Node> = emptyList(),
    )

    @Serializable
    data class AnyValueArgs(
        val value: JsonElement,
        val primitive: JsonPrimitive,
        val items: JsonArray,
    )

    @Serializable
    data class PlainArgs(val name: String, val count: Int)

    class NonSerializableArgs(val payload: JsonObject, val tags: List<String>)

    @Serializable
    data class Request(
        val body: JsonObject,
        val headers: Map<String, String>,
    )

    @Tool
    @LLMDescription("Sends a POST request")
    fun post(
        @LLMDescription("Target URL") url: String,
        @LLMDescription("Request body") payload: JsonObject,
    ): String = "$url ${payload.keys.sorted()}"

    @Tool
    fun postMany(payloads: List<JsonObject>, optional: JsonObject? = null): Int = payloads.size

    @Tool
    fun send(request: Request): String = request.body.toString()

    @Tool
    fun anyValue(value: JsonElement): String = value.toString()

    private fun ToolParameterType.nullable() = ToolParameterType.AnyOf(
        types = arrayOf(
            ToolParameterDescriptor(name = "", description = "", type = ToolParameterType.Null),
            ToolParameterDescriptor(name = "", description = "", type = this),
        )
    )

    // Serializable args (Tool<Args, Result>)

    @Test
    fun testJsonObjectInSerializableArgsIsFreeFormObject() {
        val descriptor = getToolDescriptor(typeToken<PostArgs>(), "post")

        assertEquals(
            listOf(
                ToolParameterDescriptor("url", "Target URL", ToolParameterType.String),
                ToolParameterDescriptor("payload", "Request body", freeFormObject),
            ),
            descriptor.requiredParameters,
        )
        assertEquals(
            listOf(
                ToolParameterDescriptor(
                    "headers",
                    "",
                    ToolParameterType.Object(
                        properties = emptyList(),
                        additionalProperties = true,
                        additionalPropertiesType = ToolParameterType.String,
                    )
                ),
                ToolParameterDescriptor("metadata", "", freeFormObject.nullable()),
            ),
            descriptor.optionalParameters,
        )
    }

    @Test
    fun testJsonObjectsInsideCollections() {
        val descriptor = getToolDescriptor(typeToken<CollectionArgs>(), "collections")

        assertEquals(
            listOf(
                ToolParameterDescriptor("records", "", ToolParameterType.List(freeFormObject)),
                ToolParameterDescriptor(
                    "byId",
                    "",
                    ToolParameterType.Object(
                        properties = emptyList(),
                        additionalProperties = true,
                        additionalPropertiesType = freeFormObject,
                    )
                ),
            ),
            descriptor.requiredParameters,
        )
    }

    @Test
    fun testRecursiveTypeWithJsonObject() {
        val schema = getJsonSchema(typeToken<Node>())
        val encoded = json.encodeToString(schema)

        assertTrue("\"data\":{\"type\":\"object\",\"additionalProperties\":{}}" in encoded, encoded)
    }

    @Test
    fun testAnyValueTypesGetEmptySchema() {
        val schema = json.encodeToJsonElement(getJsonSchema(typeToken<AnyValueArgs>())).jsonObject
        val properties = schema.getValue("properties").jsonObject

        assertEquals(JsonObject(emptyMap()), properties.getValue("value"))
        assertEquals(JsonObject(emptyMap()), properties.getValue("primitive"))
        assertEquals(
            buildJsonObject {
                put("type", "array")
                putJsonObject("items") {}
            },
            properties.getValue("items"),
        )
    }

    @Test
    fun testAnyValueToolParameterHasClearError() {
        val exception = assertThrows<IllegalArgumentException> {
            getToolDescriptor(typeToken<AnyValueArgs>(), "anyValue")
        }

        assertTrue("any JSON value" in exception.message.orEmpty(), exception.message)
    }

    @Test
    fun testDescriptorsWithoutJsonTypesAreNotWrapped() {
        val descriptor = serializer<PlainArgs>().descriptor

        assertSame(descriptor, descriptor.withSchemaFriendlyJsonTypes())
    }

    @Test
    fun testJsonObjectInNonSerializableClass() {
        val schema = json.encodeToJsonElement(getJsonSchema(KotlinClassToken(NonSerializableArgs::class))).jsonObject
        val properties = schema.getValue("properties").jsonObject

        assertEquals(
            buildJsonObject {
                put("type", "object")
                putJsonObject("additionalProperties") {}
            },
            properties.getValue("payload"),
        )
        assertEquals(
            buildJsonObject {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            },
            properties.getValue("tags"),
        )
    }

    // @Tool functions

    @Test
    fun testJsonObjectFunctionParameterIsFreeFormObject() {
        val descriptor = getToolDescriptor(::post)

        // Previously described as an object whose values must all be strings
        assertEquals(
            listOf(
                ToolParameterDescriptor("url", "Target URL", ToolParameterType.String),
                ToolParameterDescriptor("payload", "Request body", freeFormObject),
            ),
            descriptor.requiredParameters,
        )
        assertEquals("Sends a POST request", descriptor.description)
    }

    @Test
    fun testJsonObjectFunctionParametersInsideCollectionsAndNullable() {
        val descriptor = getToolDescriptor(::postMany)

        assertEquals(
            listOf(
                ToolParameterDescriptor("payloads", "", ToolParameterType.List(freeFormObject)),
                ToolParameterDescriptor("optional", "", freeFormObject.nullable()),
            ),
            descriptor.requiredParameters,
        )
    }

    @Test
    fun testJsonObjectNestedInFunctionParameterClass() {
        val descriptor = getToolDescriptor(::send)
        val request = descriptor.requiredParameters.single().type as ToolParameterType.Object

        assertEquals(
            listOf(
                ToolParameterDescriptor("body", "", freeFormObject),
                ToolParameterDescriptor(
                    "headers",
                    "",
                    ToolParameterType.Object(
                        properties = emptyList(),
                        additionalProperties = true,
                        additionalPropertiesType = ToolParameterType.String,
                    )
                ),
            ),
            request.properties.sortedBy { it.name },
        )
    }

    @Test
    fun testJsonElementFunctionParameterHasClearError() {
        val exception = assertThrows<IllegalArgumentException> {
            getToolDescriptor(::anyValue)
        }

        assertTrue("any JSON value" in exception.message.orEmpty(), exception.message)
    }

    @Test
    fun testToolWithJsonObjectParameterDecodesArbitraryPayload() = runTest {
        val tool = ToolFromCallable(callable = ::post, thisRef = this@JsonBuiltinTypesSchemaTest)
        val args = buildJsonObject {
            put("url", "https://example.com")
            putJsonObject("payload") {
                put("count", 3)
                put("enabled", true)
                putJsonArray("tags") { add(JsonPrimitive("a")) }
                putJsonObject("nested") { put("x", 1.5) }
            }
        }.toKoogJSONObject()

        val result = tool.execute(tool.decodeArgs(args, KotlinxSerializer()))

        assertEquals("https://example.com [count, enabled, nested, tags]", result)
    }

    class WebToolSet : ToolSet {
        @Tool
        @LLMDescription("Posts a payload")
        fun post(
            url: String,
            headers: Map<String, String>,
            payload: JsonObject,
        ): String = "ok"
    }

    @Test
    fun testToolSetFromIssue() {
        // Example from https://github.com/JetBrains/koog/issues/1921
        val tool = WebToolSet().asTools().single()

        assertEquals(freeFormObject, tool.descriptor.requiredParameters.single { it.name == "payload" }.type)
    }
}
