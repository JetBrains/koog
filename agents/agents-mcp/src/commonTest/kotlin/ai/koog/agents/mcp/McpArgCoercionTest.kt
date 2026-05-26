package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpArgCoercionTest {

    private fun descriptor(vararg required: ToolParameterDescriptor) = ToolDescriptor(
        name = "test",
        description = "test",
        requiredParameters = required.toList(),
        optionalParameters = emptyList(),
    )

    private fun optDescriptor(vararg optional: ToolParameterDescriptor) = ToolDescriptor(
        name = "test",
        description = "test",
        requiredParameters = emptyList(),
        optionalParameters = optional.toList(),
    )

    private fun param(name: String, type: ToolParameterType) =
        ToolParameterDescriptor(name = name, description = "", type = type)

    @Test
    fun `coerce stringified object to JsonObject when descriptor type is Object`() {
        val desc = descriptor(
            param(
                "payload",
                ToolParameterType.Object(
                    properties = listOf(param("k", ToolParameterType.String))
                )
            )
        )
        val args = buildJsonObject { put("payload", "{\"k\":\"v\"}") }

        val result = coerceArgsToDescriptorTypes(args, desc)

        val expected = buildJsonObject {
            put("payload", buildJsonObject { put("k", "v") })
        }
        assertEquals(expected, result)
    }

    @Test
    fun `coerce stringified array to JsonArray when descriptor type is List`() {
        val desc = descriptor(
            param("items", ToolParameterType.List(itemsType = ToolParameterType.String))
        )
        val args = buildJsonObject { put("items", "[\"a\",\"b\"]") }

        val result = coerceArgsToDescriptorTypes(args, desc)

        val expected = buildJsonObject {
            put(
                "items",
                buildJsonArray {
                    add(JsonPrimitive("a"))
                    add(JsonPrimitive("b"))
                }
            )
        }
        assertEquals(expected, result)
    }

    @Test
    fun `leave already-object value untouched`() {
        val desc = descriptor(
            param(
                "payload",
                ToolParameterType.Object(
                    properties = listOf(param("k", ToolParameterType.String))
                )
            )
        )
        val inner = buildJsonObject { put("k", "v") }
        val args = buildJsonObject { put("payload", inner) }

        val result = coerceArgsToDescriptorTypes(args, desc)

        assertEquals(args, result)
    }

    @Test
    fun `leave already-string value untouched when descriptor type is String`() {
        val desc = descriptor(param("name", ToolParameterType.String))
        val args = buildJsonObject { put("name", "{not-json-but-valid-string}") }

        val result = coerceArgsToDescriptorTypes(args, desc)

        assertEquals(args, result)
    }

    @Test
    fun `leave value untouched when JSON parse fails for Object param`() {
        val desc = descriptor(
            param("payload", ToolParameterType.Object(properties = emptyList()))
        )
        val args = buildJsonObject { put("payload", "not a json") }

        val result = coerceArgsToDescriptorTypes(args, desc)

        assertEquals(args, result)
    }

    @Test
    fun `leave value untouched when parsed shape mismatches descriptor - Object expected but array given as string`() {
        val desc = descriptor(
            param("payload", ToolParameterType.Object(properties = emptyList()))
        )
        val args = buildJsonObject { put("payload", "[1,2,3]") }

        val result = coerceArgsToDescriptorTypes(args, desc)

        assertEquals(args, result)
    }

    @Test
    fun `recursively coerce nested stringified object inside Object properties`() {
        val innerType = ToolParameterType.Object(
            properties = listOf(param("k", ToolParameterType.String))
        )
        val outerType = ToolParameterType.Object(
            properties = listOf(param("inner", innerType))
        )
        val desc = descriptor(param("outer", outerType))

        // outer is a stringified object whose "inner" value is itself a stringified object
        val args = buildJsonObject { put("outer", "{\"inner\":\"{\\\"k\\\":\\\"v\\\"}\"}") }

        val result = coerceArgsToDescriptorTypes(args, desc)

        val expected = buildJsonObject {
            put(
                "outer",
                buildJsonObject {
                    put("inner", buildJsonObject { put("k", "v") })
                }
            )
        }
        assertEquals(expected, result)
    }

    @Test
    fun `AnyOf with Object candidate coerces stringified object via that candidate`() {
        val desc = descriptor(
            param(
                "x",
                ToolParameterType.AnyOf(
                    types = arrayOf(
                        param("", ToolParameterType.Null),
                        param(
                            "",
                            ToolParameterType.Object(
                                properties = listOf(param("k", ToolParameterType.String))
                            )
                        )
                    )
                )
            )
        )
        val args = buildJsonObject { put("x", "{\"k\":\"v\"}") }

        val result = coerceArgsToDescriptorTypes(args, desc)

        val expected = buildJsonObject {
            put("x", buildJsonObject { put("k", "v") })
        }
        assertEquals(expected, result)
    }

    @Test
    fun `AnyOf without Object candidate leaves stringified array untouched`() {
        val desc = descriptor(
            param(
                "x",
                ToolParameterType.AnyOf(
                    types = arrayOf(
                        param("", ToolParameterType.Null),
                        param(
                            "",
                            ToolParameterType.Object(
                                properties = listOf(param("k", ToolParameterType.String))
                            )
                        )
                    )
                )
            )
        )
        // array is not compatible with any candidate (Null or Object)
        val args = buildJsonObject { put("x", "[1,2,3]") }

        val result = coerceArgsToDescriptorTypes(args, desc)

        assertEquals(args, result)
    }

    @Test
    fun `additionalPropertiesType coerces unmatched keys when configured`() {
        val additionalType = ToolParameterType.Object(
            properties = listOf(param("v", ToolParameterType.String))
        )
        val desc = descriptor(
            param(
                "obj",
                ToolParameterType.Object(
                    properties = listOf(param("k1", ToolParameterType.String)),
                    additionalProperties = true,
                    additionalPropertiesType = additionalType,
                )
            )
        )
        val args = buildJsonObject {
            put(
                "obj",
                buildJsonObject {
                    put("k1", "ok")
                    put("k2", "{\"v\":\"x\"}")
                }
            )
        }

        val result = coerceArgsToDescriptorTypes(args, desc)

        val expected = buildJsonObject {
            put(
                "obj",
                buildJsonObject {
                    put("k1", "ok")
                    put("k2", buildJsonObject { put("v", "x") })
                }
            )
        }
        assertEquals(expected, result)
    }

    @Test
    fun `additionalProperties == false leaves unmatched keys untouched`() {
        val desc = descriptor(
            param(
                "obj",
                ToolParameterType.Object(
                    properties = listOf(param("k1", ToolParameterType.String)),
                    additionalProperties = false,
                )
            )
        )
        val args = buildJsonObject {
            put(
                "obj",
                buildJsonObject {
                    put("k1", "ok")
                    put("k2", "{\"v\":\"x\"}")
                }
            )
        }

        val result = coerceArgsToDescriptorTypes(args, desc)

        assertEquals(args, result)
    }

    @Test
    fun `depth-bounded recursion stops at MAX_DEPTH and preserves original`() {
        // Build a descriptor that nests Object 9 levels deep (exceeds MAX_COERCION_DEPTH = 8)
        fun nestedObjectType(depth: Int): ToolParameterType.Object {
            return if (depth <= 1) {
                ToolParameterType.Object(properties = listOf(param("leaf", ToolParameterType.String)))
            } else {
                ToolParameterType.Object(properties = listOf(param("nested", nestedObjectType(depth - 1))))
            }
        }

        val desc = descriptor(param("root", nestedObjectType(9)))

        // Build args: root is a stringified object that is itself 9 levels deep of stringified objects.
        // Innermost is the real {"leaf":"v"}; each outer level wraps the previous level as a
        // stringified JSON value.
        var innerJson = "{\"leaf\":\"v\"}"
        repeat(8) {
            innerJson = "{\"nested\":${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(innerJson))}}"
        }
        val args = buildJsonObject { put("root", innerJson) }

        // Should not throw; the depth guard must prevent stack overflow.
        val result = coerceArgsToDescriptorTypes(args, desc)

        // Verify the exact boundary from plan §3.4 / §4.1 case 12:
        // depths 1..8 must be unwrapped to JsonObject, and the value at depth 9 — which would
        // require crossing MAX_COERCION_DEPTH — must remain the original string.
        val rootObj = result["root"] as JsonObject
        var node: JsonElement = rootObj
        repeat(7) { node = (node as JsonObject)["nested"]!! }
        assertTrue(node is JsonObject, "depth 8 should be unwrapped to JsonObject but was $node")
        val depth9 = (node as JsonObject)["nested"]!!
        assertTrue(
            depth9 is JsonPrimitive && depth9.isString,
            "depth 9 should remain a string but was $depth9"
        )
    }
}
