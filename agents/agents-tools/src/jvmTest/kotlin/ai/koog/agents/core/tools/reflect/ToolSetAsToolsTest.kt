package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
object TestToolsEnabler : DirectToolCallsEnabler

interface MathToolsInterface : ToolSet {
    @Tool
    @LLMDescription("Adds two numbers")
    fun add(
        @LLMDescription("First number") a: Int,
        @LLMDescription("Second number") b: Int
    ): Int

    @Tool
    @LLMDescription("Subtracts second number from first")
    fun subtract(
        @LLMDescription("First number") a: Int,
        @LLMDescription("Second number") b: Int
    ): Int
}

class MathToolsImpl : MathToolsInterface {
    override fun add(a: Int, b: Int): Int = a + b
    override fun subtract(a: Int, b: Int): Int = a - b

    @Tool
    @LLMDescription("Multiplies two numbers")
    fun multiply(
        @LLMDescription("First number") a: Int,
        @LLMDescription("Second number") b: Int
    ): Int = a * b

    @Tool
    @LLMDescription("Divides first number by second")
    fun divide(
        @LLMDescription("First number") a: Int,
        @LLMDescription("Second number") b: Int
    ): Int = a / b
}

interface AdvancedMathToolsInterface : ToolSet {
    @Tool
    @LLMDescription("Calculates the power of a number")
    fun power(
        @LLMDescription("Base number") base: Int,
        @LLMDescription("Exponent") exponent: Int
    ): Int
}

class CombinedMathToolsImpl : MathToolsInterface, AdvancedMathToolsInterface {
    override fun add(a: Int, b: Int): Int = a + b
    override fun subtract(a: Int, b: Int): Int = a - b
    override fun power(base: Int, exponent: Int): Int = base.toDouble().pow(exponent.toDouble()).toInt()
}

@LLMDescription("A set of string manipulation tools")
class StringToolsImpl : ToolSet {
    @Tool
    @LLMDescription("Concatenates two strings")
    fun concat(
        @LLMDescription("First string") a: String,
        @LLMDescription("Second string") b: String
    ): String = a + b

    @Tool
    @LLMDescription("Returns the length of a string")
    fun length(
        @LLMDescription("Input string") input: String
    ): Int = input.length

    // Not marked as a tool, should not be included
    fun uppercase(input: String): String = input.uppercase()
}

@Serializable
data class Person(val name: String, val age: Int)

class ComplexToolsImpl : ToolSet {
    @Tool
    @LLMDescription("Creates a person object")
    fun createPerson(
        @LLMDescription("Person's name") name: String,
        @LLMDescription("Person's age") age: Int
    ): Person = Person(name, age)

    @Tool
    @LLMDescription("Formats a person's details")
    fun formatPerson(
        @LLMDescription("Person object") person: Person
    ): String = "${person.name} is ${person.age} years old"
}

@OptIn(InternalAgentToolsApi::class)
class ToolSetAsToolsTest {
    private val json = Json

    @Test
    @OptIn(InternalAgentToolsApi::class)
    fun testToolSetName() {
        val stringTools = StringToolsImpl()
        assertEquals("A set of string manipulation tools", stringTools.name, "Name should come from LLMDescription")

        val mathTools = MathToolsImpl()
        assertTrue(mathTools.name.contains("MathToolsImpl"), "Name should default to class name")
    }

    @Test
    @OptIn(InternalAgentToolsApi::class)
    fun testMathToolsExecution() = runTest {
        val mathTools = MathToolsImpl()
        val tools = mathTools.asTools(json)

        val addTool = tools.find { it.descriptor.name == "add" }
        assertNotNull(addTool, "Add tool should be found")

        val addArgs = buildJsonObject {
            put("a", JsonPrimitive(5))
            put("b", JsonPrimitive(3))
        }

        val addResult = addTool.execute(addTool.decodeArgs(addArgs), TestToolsEnabler)
        assertEquals("8", addTool.encodeResultToStringUnsafe(addResult), "Add tool should return 8")

        val multiplyTool = tools.find { it.descriptor.name == "multiply" }
        assertNotNull(multiplyTool, "Multiply tool should be found")

        val multiplyArgs = buildJsonObject {
            put("a", JsonPrimitive(4))
            put("b", JsonPrimitive(7))
        }

        val multiplyResult = multiplyTool.execute(multiplyTool.decodeArgs(multiplyArgs), TestToolsEnabler)
        assertEquals("28", multiplyTool.encodeResultToStringUnsafe(multiplyResult), "Multiply tool should return 28")
    }

    @Test
    @OptIn(InternalAgentToolsApi::class)
    fun testCombinedMathToolsExecution() = runTest {
        val combinedMathTools = CombinedMathToolsImpl()
        val tools = combinedMathTools.asTools(json)

        assertEquals(3, tools.size, "Combined math tools should have 3 tools")

        val powerTool = tools.find { it.descriptor.name == "power" }
        assertNotNull(powerTool, "Power tool should be found")

        val powerArgs = buildJsonObject {
            put("base", JsonPrimitive(2))
            put("exponent", JsonPrimitive(3))
        }

        val powerResult = powerTool.execute(powerTool.decodeArgs(powerArgs), TestToolsEnabler)
        assertEquals("8", powerTool.encodeResultToStringUnsafe(powerResult), "Power tool should return 8")
    }

    @Test
    @OptIn(InternalAgentToolsApi::class)
    fun testAsToolsByInterface() = runTest {
        val mathTools = MathToolsImpl()
        val tools = mathTools.asToolsByInterface<MathToolsInterface>(json)

        assertEquals(2, tools.size, "Should only have 2 tools from the interface")

        val subtractTool = tools.find { it.descriptor.name == "subtract" }
        assertNotNull(subtractTool, "Subtract tool should be found")

        val subtractArgs = buildJsonObject {
            put("a", JsonPrimitive(10))
            put("b", JsonPrimitive(4))
        }

        val subtractResult = subtractTool.execute(subtractTool.decodeArgs(subtractArgs), TestToolsEnabler)
        assertEquals("6", subtractTool.encodeResultToStringUnsafe(subtractResult), "Subtract tool should return 6")
    }

    @Test
    @OptIn(InternalAgentToolsApi::class)
    fun testStringToolsExecution() = runTest {
        val stringTools = StringToolsImpl()
        val tools = stringTools.asTools(json)

        assertEquals(2, tools.size, "String tools should have 2 tools")

        val concatTool = tools.find { it.descriptor.name == "concat" }
        assertNotNull(concatTool, "Concat tool should be found")

        val concatArgs = buildJsonObject {
            put("a", JsonPrimitive("Hello, "))
            put("b", JsonPrimitive("World!"))
        }

        val concatResult = concatTool.execute(concatTool.decodeArgs(concatArgs), TestToolsEnabler)
        assertEquals("\"Hello, World!\"", concatTool.encodeResultToStringUnsafe(concatResult), "Concat tool should return \"Hello, World!\"")
    }

    @Test
    @OptIn(InternalAgentToolsApi::class)
    fun testComplexToolsExecution() = runTest {
        val complexTools = ComplexToolsImpl()
        val tools = complexTools.asTools(json)

        val createPersonTool = tools.find { it.descriptor.name == "createPerson" }
        assertNotNull(createPersonTool, "CreatePerson tool should be found")

        val createPersonArgs = buildJsonObject {
            put("name", JsonPrimitive("John"))
            put("age", JsonPrimitive(30))
        }

        val createPersonResult =
            createPersonTool.execute(createPersonTool.decodeArgs(createPersonArgs), TestToolsEnabler)
        val personJson = createPersonTool.encodeResultToStringUnsafe(createPersonResult)
        assertTrue(personJson.contains("\"name\":\"John\""), "Person JSON should contain name")
        assertTrue(personJson.contains("\"age\":30"), "Person JSON should contain age")

        val formatPersonTool = tools.find { it.descriptor.name == "formatPerson" }
        assertNotNull(formatPersonTool, "FormatPerson tool should be found")

        val formatPersonArgs = buildJsonObject {
            put("person", Json.parseToJsonElement(personJson))
        }

        val formatPersonResult =
            formatPersonTool.execute(formatPersonTool.decodeArgs(formatPersonArgs), TestToolsEnabler)
        assertEquals(
            "\"John is 30 years old\"",
            formatPersonTool.encodeResultToStringUnsafe(formatPersonResult),
            "Format tool should return formatted string"
        )
    }
}
