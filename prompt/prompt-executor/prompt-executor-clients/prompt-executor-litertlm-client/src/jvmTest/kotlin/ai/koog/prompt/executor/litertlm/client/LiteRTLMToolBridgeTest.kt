package ai.koog.prompt.executor.litertlm.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class LiteRTLMToolBridgeTest {

    private val weatherTool = ToolDescriptor(
        name = "get_weather",
        description = "Get the current weather for a location",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "city",
                description = "The city name",
                type = ToolParameterType.String
            )
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "units",
                description = "Temperature units (celsius or fahrenheit)",
                type = ToolParameterType.String
            )
        )
    )

    private val calculatorTool = ToolDescriptor(
        name = "calculate",
        description = "Evaluate a mathematical expression",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "expression",
                description = "The math expression to evaluate",
                type = ToolParameterType.String
            )
        )
    )

    @Test
    fun `callTool dispatches to executor with correct arguments`() {
        var capturedName: String? = null
        var capturedArgs: Map<String, Any?>? = null

        val bridge = LiteRTLMToolBridge(listOf(weatherTool)) { name, args ->
            capturedName = name
            capturedArgs = args
            """{"temperature": 25, "unit": "celsius"}"""
        }

        val result = bridge.callTool("get_weather", """{"city": "Paris"}""")

        capturedName shouldBe "get_weather"
        capturedArgs shouldBe mapOf("city" to "Paris")
        result shouldContain "temperature"
    }

    @Test
    fun `callTool handles optional parameters`() {
        var capturedArgs: Map<String, Any?>? = null

        val bridge = LiteRTLMToolBridge(listOf(weatherTool)) { _, args ->
            capturedArgs = args
            "OK"
        }

        bridge.callTool("get_weather", """{"city": "London", "units": "fahrenheit"}""")

        capturedArgs shouldBe mapOf("city" to "London", "units" to "fahrenheit")
    }

    @Test
    fun `callTool returns error for unknown tool`() {
        val bridge = LiteRTLMToolBridge(listOf(weatherTool)) { _, _ -> "OK" }

        val result = bridge.callTool("unknown_tool", "{}")

        result shouldContain "error"
        result shouldContain "Unknown tool"
        result shouldContain "get_weather"
    }

    @Test
    fun `callTool returns error for invalid JSON`() {
        val bridge = LiteRTLMToolBridge(listOf(weatherTool)) { _, _ -> "OK" }

        val result = bridge.callTool("get_weather", "not valid json")

        result shouldContain "error"
        result shouldContain "Invalid arguments"
    }

    @Test
    fun `callTool returns error for missing required parameter`() {
        val bridge = LiteRTLMToolBridge(listOf(weatherTool)) { _, _ -> "OK" }

        val result = bridge.callTool("get_weather", """{"units": "celsius"}""")

        result shouldContain "error"
        result shouldContain "city"
    }

    @Test
    fun `callTool handles executor exception`() {
        val bridge = LiteRTLMToolBridge(listOf(weatherTool)) { _, _ ->
            throw RuntimeException("Network error")
        }

        val result = bridge.callTool("get_weather", """{"city": "Paris"}""")

        result shouldContain "error"
        result shouldContain "execution failed"
    }

    @Test
    fun `generateToolsPrompt includes all tools`() {
        val bridge = LiteRTLMToolBridge(listOf(weatherTool, calculatorTool)) { _, _ -> "OK" }

        val prompt = bridge.generateToolsPrompt()

        prompt shouldContain "get_weather"
        prompt shouldContain "Get the current weather"
        prompt shouldContain "city"
        prompt shouldContain "calculate"
        prompt shouldContain "mathematical expression"
    }

    @Test
    fun `generateToolsPrompt marks required vs optional parameters`() {
        val bridge = LiteRTLMToolBridge(listOf(weatherTool)) { _, _ -> "OK" }

        val prompt = bridge.generateToolsPrompt()

        prompt shouldContain "city"
        prompt shouldContain "required"
        prompt shouldContain "units"
        prompt shouldContain "optional"
    }

    @Test
    fun `callTool handles integer parameters`() {
        val toolWithInt = ToolDescriptor(
            name = "repeat",
            description = "Repeat text",
            requiredParameters = listOf(
                ToolParameterDescriptor("text", "Text to repeat", ToolParameterType.String),
                ToolParameterDescriptor("count", "Number of times", ToolParameterType.Integer)
            )
        )

        var capturedArgs: Map<String, Any?>? = null
        val bridge = LiteRTLMToolBridge(listOf(toolWithInt)) { _, args ->
            capturedArgs = args
            "OK"
        }

        bridge.callTool("repeat", """{"text": "hello", "count": 3}""")

        capturedArgs?.get("count") shouldBe 3
    }

    @Test
    fun `callTool handles boolean parameters`() {
        val toolWithBool = ToolDescriptor(
            name = "format",
            description = "Format text",
            requiredParameters = listOf(
                ToolParameterDescriptor("text", "Text to format", ToolParameterType.String),
                ToolParameterDescriptor("uppercase", "Convert to uppercase", ToolParameterType.Boolean)
            )
        )

        var capturedArgs: Map<String, Any?>? = null
        val bridge = LiteRTLMToolBridge(listOf(toolWithBool)) { _, args ->
            capturedArgs = args
            "OK"
        }

        bridge.callTool("format", """{"text": "hello", "uppercase": true}""")

        capturedArgs?.get("uppercase") shouldBe true
    }

    @Test
    fun `callTool handles array parameters`() {
        val toolWithArray = ToolDescriptor(
            name = "sum",
            description = "Sum numbers",
            requiredParameters = listOf(
                    ToolParameterDescriptor(
                        "numbers",
                        "Numbers to sum",
                        ToolParameterType.List(ToolParameterType.Integer)
                    )
                )
        )

        var capturedArgs: Map<String, Any?>? = null
        val bridge = LiteRTLMToolBridge(listOf(toolWithArray)) { _, args ->
            capturedArgs = args
            "OK"
        }

        bridge.callTool("sum", """{"numbers": [1, 2, 3, 4, 5]}""")

        @Suppress("UNCHECKED_CAST")
        val numbers = capturedArgs?.get("numbers") as? List<Int>
        numbers shouldBe listOf(1, 2, 3, 4, 5)
    }

    @Test
    fun `callTool handles empty arguments`() {
        val toolNoArgs = ToolDescriptor(
            name = "get_time",
            description = "Get current time"
        )

        var called = false
        val bridge = LiteRTLMToolBridge(listOf(toolNoArgs)) { _, args ->
            called = true
            args shouldBe emptyMap()
            "12:00"
        }

        bridge.callTool("get_time", "{}")

        called shouldBe true
    }
}
