package ai.koog.prompt.executor.clients.bedrock.modelfamilies

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BedrockToolSerializationTest {
    private val namePropertyName = "name"
    private val namePropertyDesc = "User name"
    private val objectParamName = "user"
    private val objectParamDesc = "User information"

    @Test
    fun `test buildToolParameterSchema with String parameter`() {
        val stringParamDesc = "Search query"

        val param = ToolParameterDescriptor(
            name = "query",
            description = stringParamDesc,
            type = ToolParameterType.String
        )

        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)
        assertEquals(stringParamDesc, schema["description"]?.jsonPrimitive?.content)
        assertEquals("string", schema["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test buildToolParameterSchema with Integer parameter`() {
        val paramDesc = "Number of results"

        val param = ToolParameterDescriptor(
            name = "count",
            description = paramDesc,
            type = ToolParameterType.Integer
        )

        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)
        assertEquals(paramDesc, schema["description"]?.jsonPrimitive?.content)
        assertEquals("integer", schema["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test buildToolParameterSchema with Float parameter`() {
        val paramDesc = "Temperature value"

        val param = ToolParameterDescriptor(
            name = "temperature",
            description = paramDesc,
            type = ToolParameterType.Float
        )

        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)
        assertEquals(paramDesc, schema["description"]?.jsonPrimitive?.content)
        assertEquals("number", schema["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test buildToolParameterSchema with Boolean parameter`() {
        val paramDesc = "Feature toggle"

        val param = ToolParameterDescriptor(
            name = "enabled",
            description = paramDesc,
            type = ToolParameterType.Boolean
        )

        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)
        assertEquals(paramDesc, schema["description"]?.jsonPrimitive?.content)
        assertEquals("boolean", schema["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test buildToolParameterSchema with Enum parameter`() {
        val paramDesc = "Output format"

        val param = ToolParameterDescriptor(
            name = "format",
            description = paramDesc,
            type = ToolParameterType.Enum(arrayOf("json", "xml", "text"))
        )

        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)
        assertEquals(paramDesc, schema["description"]?.jsonPrimitive?.content)
        assertEquals("string", schema["type"]?.jsonPrimitive?.content)

        val enumValues = schema["enum"]?.jsonArray
        assertNotNull(enumValues)
        assertEquals(3, enumValues.size)

        assertTrue(enumValues.any { it.toString().contains("json") })
        assertTrue(enumValues.any { it.toString().contains("xml") })
        assertTrue(enumValues.any { it.toString().contains("text") })
    }

    @Test
    fun `test buildToolParameterSchema with List parameter`() {
        val paramDesc = "List of users"

        val param = ToolParameterDescriptor(
            name = "user",
            description = paramDesc,
            type = ToolParameterType.List(ToolParameterType.String)
        )

        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)
        assertEquals(paramDesc, schema["description"]?.jsonPrimitive?.content)
        assertEquals("array", schema["type"]?.jsonPrimitive?.content)

        val items = schema["items"]?.jsonObject
        assertNotNull(items)
        assertEquals("string", items["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test buildToolParameterSchema with List of Integer parameter`() {
        val paramDesc = "List of IDs"

        val param = ToolParameterDescriptor(
            name = "List",
            description = paramDesc,
            type = ToolParameterType.List(ToolParameterType.Integer)
        )

        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)
        assertEquals(paramDesc, schema["description"]?.jsonPrimitive?.content)
        assertEquals("array", schema["type"]?.jsonPrimitive?.content)

        val items = schema["items"]?.jsonObject
        assertNotNull(items)
        assertEquals("integer", items["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test buildToolParameterSchema with Object parameter`() {
        val namePropertyName = "name"
        val namePropertyDesc = "User name"
        val agePropertyName = "age"
        val agePropertyDesc = "User age"

        val objectType = ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor(namePropertyName, namePropertyDesc, ToolParameterType.String),
                ToolParameterDescriptor(agePropertyName, agePropertyDesc, ToolParameterType.Integer)
            )
        )

        val param = ToolParameterDescriptor(
            name = objectParamName,
            description = objectParamDesc,
            type = objectType
        )

        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)
        assertEquals(objectParamDesc, schema["description"]?.jsonPrimitive?.content)
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)

        val properties = schema["properties"]?.jsonObject
        assertNotNull(properties)

        val nameProperty = properties[namePropertyName]?.jsonObject
        assertNotNull(nameProperty)
        assertEquals(namePropertyDesc, nameProperty["description"]?.jsonPrimitive?.content)
        assertEquals("string", nameProperty["type"]?.jsonPrimitive?.content)

        val ageProperty = properties[agePropertyName]?.jsonObject
        assertNotNull(ageProperty)
        assertEquals(agePropertyDesc, ageProperty["description"]?.jsonPrimitive?.content)
        assertEquals("integer", ageProperty["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test buildToolParameterSchema with nested Object parameter`() {
        val streetPropertyName = "street"
        val streetPropertyDesc = "Street address"
        val cityPropertyName = "city"
        val cityPropertyDesc = "City name"
        val addressPropertyName = "address"
        val addressPropertyDesc = "User address"

        val addressType = ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor(streetPropertyName, streetPropertyDesc, ToolParameterType.String),
                ToolParameterDescriptor(cityPropertyName, cityPropertyDesc, ToolParameterType.String)
            )
        )

        val userType = ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor(namePropertyName, namePropertyDesc, ToolParameterType.String),
                ToolParameterDescriptor(addressPropertyName, addressPropertyDesc, addressType)
            )
        )

        val param = ToolParameterDescriptor(
            name = objectParamName,
            description = objectParamDesc,
            type = userType
        )

        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)
        assertEquals(objectParamDesc, schema["description"]?.jsonPrimitive?.content)
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)

        val properties = schema["properties"]?.jsonObject
        assertNotNull(properties)

        val nameProperty = properties[namePropertyName]?.jsonObject
        assertNotNull(nameProperty)
        assertEquals(namePropertyDesc, nameProperty["description"]?.jsonPrimitive?.content)
        assertEquals("string", nameProperty["type"]?.jsonPrimitive?.content)

        val addressProperty = properties[addressPropertyName]?.jsonObject
        assertNotNull(addressProperty)
        assertEquals(addressPropertyDesc, addressProperty["description"]?.jsonPrimitive?.content)
        assertEquals("object", addressProperty["type"]?.jsonPrimitive?.content)

        val addressProperties = addressProperty["properties"]?.jsonObject
        assertNotNull(addressProperties)

        val streetProperty = addressProperties[streetPropertyName]?.jsonObject
        assertNotNull(streetProperty)
        assertEquals(streetPropertyDesc, streetProperty["description"]?.jsonPrimitive?.content)
        assertEquals("string", streetProperty["type"]?.jsonPrimitive?.content)

        val cityProperty = addressProperties[cityPropertyName]?.jsonObject
        assertNotNull(cityProperty)
        assertEquals(cityPropertyDesc, cityProperty["description"]?.jsonPrimitive?.content)
        assertEquals("string", cityProperty["type"]?.jsonPrimitive?.content)
    }
}