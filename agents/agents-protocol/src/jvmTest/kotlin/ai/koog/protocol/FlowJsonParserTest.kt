package ai.koog.protocol

import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.agent.agents.transform.FlowInputTransformAgent
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgent
import ai.koog.protocol.flow.ConditionOperationKind
import ai.koog.protocol.parser.FlowJsonConfigParser
import ai.koog.protocol.tool.FlowTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FlowJsonParserTest : FlowTestBase() {

    //region Flow

    @Test
    fun testJsonParsing_randomNumbersFlowJson() {
        val jsonContent = readFlow("random_koog_agent_flow.json")

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow config
        assertEquals("random-numbers-flow", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt-4o", flowConfig.defaultModel)

        // Verify agents
        assertEquals(2, flowConfig.agents.size)

        // get_numbers
        val getNumbersAgent = flowConfig.agents[0]
        assertIs<FlowTaskAgent>(getNumbersAgent)
        assertEquals("get_numbers", getNumbersAgent.name)
        assertEquals(FlowAgentKind.TASK, getNumbersAgent.type)
        assertEquals("openai/gpt-4o", getNumbersAgent.model, "Expected to get a default model, but received ${getNumbersAgent.model}")
        assertNotNull(getNumbersAgent.parameters)
        assertEquals(
            "Generate two random numbers between 1 and 100. Output them with a space between them.",
            getNumbersAgent.parameters.task
        )

        // Verify the second agent (calculator) - overrides with an own model
        val calculatorAgent = flowConfig.agents[1]
        assertIs<FlowTaskAgent>(calculatorAgent)
        assertEquals("calculator", calculatorAgent.name)
        assertEquals(FlowAgentKind.TASK, calculatorAgent.type)
        assertEquals("openai/gpt-4o-mini", calculatorAgent.model, "Expected to get a custom model, but received ${calculatorAgent.model}")
        assertNotNull(calculatorAgent.parameters)
        assertEquals(
            "Your task is to sum all individual numbers in the input string. Numbers are separated by spaces.",
            calculatorAgent.parameters.task
        )

        // Verify transition
        assertEquals(1, flowConfig.transitions.size)

        val transition = flowConfig.transitions[0]
        assertEquals("get_numbers", transition.from)
        assertEquals("calculator", transition.to)
    }

    //endregion Flow

    //region Tools

    @Test
    fun testFlowParsing_withMcpTools() {
        val jsonContent = readFlow("random_koog_agent_flow_with_mcp_tools.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow metadata
        assertEquals("random-numbers-flow-with-mcp-tools", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt-4o", flowConfig.defaultModel)

        // Verify tools are parsed correctly
        assertEquals(2, flowConfig.tools.size)

        // First tool: MCP SSE
        val sseTool = flowConfig.tools[0]
        assertIs<FlowTool.Mcp.SSE>(sseTool)
        assertEquals("http://localhost:8931/sse", sseTool.url)
        assertEquals(emptyMap(), sseTool.headers)

        // Second tool: MCP Stdio
        val stdioTool = flowConfig.tools[1]
        assertIs<FlowTool.Mcp.Stdio>(stdioTool)
        assertEquals("npx", stdioTool.command)
        assertEquals(listOf("-y", "@modelcontextprotocol/server-github"), stdioTool.args)

        // Verify agents are still parsed correctly
        assertEquals(2, flowConfig.agents.size)
        assertEquals("get_numbers", flowConfig.agents[0].name)
        assertEquals("calculator", flowConfig.agents[1].name)

        // Verify transitions are still parsed correctly
        assertEquals(1, flowConfig.transitions.size)
        assertEquals("get_numbers", flowConfig.transitions[0].from)
        assertEquals("calculator", flowConfig.transitions[0].to)
    }

    @Test
    fun testFlowParsing_withMcpSseToolWithHeaders() {
        val jsonContent = """
        {
            "id": "test-flow",
            "version": "1.0",
            "defaultModel": "openai/gpt-4o",
            "tools": [
                {
                    "name": "authenticated-mcp-server",
                    "type": "mcp",
                    "parameters": {
                        "transport": "sse",
                        "url": "http://localhost:9000/sse",
                        "headers": {
                            "Authorization": "Bearer token123",
                            "X-Custom-Header": "custom-value"
                        }
                    }
                }
            ],
            "agents": [],
            "transitions": []
        }
        """.trimIndent()

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        assertEquals(1, flowConfig.tools.size)

        val sseTool = flowConfig.tools[0]
        assertIs<FlowTool.Mcp.SSE>(sseTool)
        assertEquals("http://localhost:9000/sse", sseTool.url)
        assertEquals(
            mapOf(
                "Authorization" to "Bearer token123",
                "X-Custom-Header" to "custom-value"
            ),
            sseTool.headers
        )
    }

    @Test
    fun testFlowParsing_withMcpStdioToolMinimalArgs() {
        val jsonContent = """
        {
            "id": "test-flow",
            "version": "1.0",
            "defaultModel": "openai/gpt-4o",
            "tools": [
                {
                    "name": "simple-stdio-tool",
                    "type": "mcp",
                    "parameters": {
                        "transport": "stdio",
                        "command": "python3"
                    }
                }
            ],
            "agents": [],
            "transitions": []
        }
        """.trimIndent()

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        assertEquals(1, flowConfig.tools.size)

        val stdioTool = flowConfig.tools[0]
        assertIs<FlowTool.Mcp.Stdio>(stdioTool)
        assertEquals("python3", stdioTool.command)
        assertEquals(emptyList(), stdioTool.args)
    }

    //endregion Tools

    //region Verify and Transform

    @Test
    fun testJsonParsing_verifyTransformFlowJson() {
        val jsonContent = readFlow("verify_transform_flow.json")

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow config
        assertEquals("verify-transform-flow", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt-4o", flowConfig.defaultModel)

        // Verify agents
        assertEquals(4, flowConfig.agents.size)

        // task_agent
        val taskAgent = flowConfig.agents[0]
        assertIs<FlowTaskAgent>(taskAgent)
        assertEquals("task_agent", taskAgent.name)
        assertEquals(FlowAgentKind.TASK, taskAgent.type)
        assertEquals("Generate a simple greeting message.", taskAgent.parameters.task)

        // verify_agent
        val verifyAgent = flowConfig.agents[1]
        assertIs<FlowVerifyAgent>(verifyAgent)
        assertEquals("verify_agent", verifyAgent.name)
        assertEquals(FlowAgentKind.VERIFY, verifyAgent.type)
        assertEquals("Verify that the input contains a valid greeting message.", verifyAgent.parameters.task)

        // transform_feedback
        val transformAgent = flowConfig.agents[2]
        assertIs<FlowInputTransformAgent>(transformAgent)
        assertEquals("transform_feedback", transformAgent.name)
        assertEquals(FlowAgentKind.TRANSFORM, transformAgent.type)
        assertEquals(1, transformAgent.parameters.transformations.size)
        assertEquals("input.feedback", transformAgent.parameters.transformations[0].value)

        // fix_agent
        val fixAgent = flowConfig.agents[3]
        assertIs<FlowTaskAgent>(fixAgent)
        assertEquals("fix_agent", fixAgent.name)
        assertEquals(FlowAgentKind.TASK, fixAgent.type)
        assertEquals("Fix the issue based on the provided feedback.", fixAgent.parameters.task)

        // Verify transitions
        assertEquals(4, flowConfig.transitions.size)

        // task_agent -> verify_agent (unconditional)
        val transition1 = flowConfig.transitions[0]
        assertEquals("task_agent", transition1.from)
        assertEquals("verify_agent", transition1.to)
        assertEquals(null, transition1.condition)

        // verify_agent -> __finish__ (condition: success == true)
        val transition2 = flowConfig.transitions[1]
        assertEquals("verify_agent", transition2.from)
        assertEquals("__finish__", transition2.to)
        assertNotNull(transition2.condition)
        assertEquals("input.success", transition2.condition!!.variable)
        assertEquals(ConditionOperationKind.EQUALS, transition2.condition!!.operation)
        assertTrue(transition2.condition!!.value.isPrimitive)

        // verify_agent -> transform_feedback (condition: success == false)
        val transition3 = flowConfig.transitions[2]
        assertEquals("verify_agent", transition3.from)
        assertEquals("transform_feedback", transition3.to)
        assertNotNull(transition3.condition)
        assertEquals("input.success", transition3.condition!!.variable)
        assertEquals(ConditionOperationKind.EQUALS, transition3.condition!!.operation)

        // transform_feedback -> fix_agent (unconditional)
        val transition4 = flowConfig.transitions[3]
        assertEquals("transform_feedback", transition4.from)
        assertEquals("fix_agent", transition4.to)
        assertEquals(null, transition4.condition)
    }

    //endregion Verify and Transform
}
