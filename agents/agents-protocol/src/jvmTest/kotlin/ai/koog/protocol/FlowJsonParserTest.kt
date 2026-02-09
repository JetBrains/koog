package ai.koog.protocol

import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.agents.react.FlowReActAgent
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.agent.agents.transform.FlowInputTransformAgent
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgent
import ai.koog.protocol.flow.ConditionOperationKind
import ai.koog.protocol.parser.FlowJsonConfigParser
import ai.koog.protocol.tool.FlowTool
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FlowJsonParserTest : FlowTestBase() {

    companion object {
        @JvmStatic
        fun getResourceJsonNames(): Array<String> {
            val resourcePath = object {}.javaClass.getResource("/json")?.let { Path(it.path) }
                ?: error("Could not find '/resource/json' directory")

            val exampleJsonFiles = resourcePath.listDirectoryEntries()
                .filter { entry -> entry.extension.equals("json", ignoreCase = true) }
                .map { it.name }

            return exampleJsonFiles.toTypedArray()
        }
    }

    //region Examples Parsing

    @ParameterizedTest
    @MethodSource("getResourceJsonNames")
    fun testAllFlowJsonFilesCanBeParsed(jsonFileName: String) {
        val parser = FlowJsonConfigParser()

        val jsonContent = readFlow(jsonFileName)
        val flowConfig = parser.parse(jsonContent)

        assertNotNull(flowConfig, "Failed to parse $jsonFileName")
        assertTrue(flowConfig.agents.isNotEmpty(), "$jsonFileName should have agents")

        // Verify all transitions reference valid agents
        val agentNames = flowConfig.agents.map { it.name }.toSet()
        flowConfig.transitions.forEach { transition ->
            assertTrue(
                agentNames.contains(transition.from),
                "$jsonFileName: transition.from '${transition.from}' not found in agents"
            )
            assertTrue(
                agentNames.contains(transition.to) || transition.to == "__finish__",
                "$jsonFileName: transition.to '${transition.to}' not found in agents"
            )
        }
    }

    //endregion Examples Parsing

    //region Flow

    @Test
    fun testJsonParsing_basicTaskFlowJson() {
        val jsonContent = readFlow("json/basic_task_flow.json")

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow config
        assertEquals("basic-task-flow", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt4o", flowConfig.defaultModel)

        // Verify agents
        assertEquals(2, flowConfig.agents.size)

        // number_generator
        val numberGeneratorAgent = flowConfig.agents[0]
        assertIs<FlowTaskAgent>(numberGeneratorAgent)
        assertEquals("number_generator", numberGeneratorAgent.name)
        assertEquals(FlowAgentKind.TASK, numberGeneratorAgent.type)
        assertEquals("openai/gpt4o", numberGeneratorAgent.model, "Expected to get a default model, but received ${numberGeneratorAgent.model}")
        assertNotNull(numberGeneratorAgent.parameters)
        assertEquals(
            "Generate two random integers between 1 and 100, separated by a space.",
            numberGeneratorAgent.parameters.task
        )

        // Verify the second agent (calculator) - overrides with an own model
        val calculatorAgent = flowConfig.agents[1]
        assertIs<FlowTaskAgent>(calculatorAgent)
        assertEquals("calculator", calculatorAgent.name)
        assertEquals(FlowAgentKind.TASK, calculatorAgent.type)
        assertEquals("openai/gpt4o", calculatorAgent.model, "Expected to get a custom model, but received ${calculatorAgent.model}")
        assertNotNull(calculatorAgent.parameters)
        assertEquals(
            "Sum all numbers in the input (numbers are space-separated).",
            calculatorAgent.parameters.task
        )

        // Verify transition
        assertEquals(1, flowConfig.transitions.size)

        val transition = flowConfig.transitions[0]
        assertEquals("number_generator", transition.from)
        assertEquals("calculator", transition.to)
    }

    //endregion Flow

    //region Tools

    @Test
    fun testFlowParsing_withMcpTools() {
        val jsonContent = readFlow("json/greeting_flow_with_mcp_tool.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow metadata
        assertEquals("greeting-flow-with-mcp-tool", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt4o", flowConfig.defaultModel)

        // Verify tools are parsed correctly
        assertEquals(2, flowConfig.tools.size)

        // First tool: MCP SSE
        val sseTool = flowConfig.tools[0]
        assertIs<FlowTool.Mcp.SSE>(sseTool)
        assertEquals("http://localhost:3002", sseTool.url)
        assertEquals(emptyMap(), sseTool.headers)

        // Second tool: MCP Stdio
        val stdioTool = flowConfig.tools[1]
        assertIs<FlowTool.Mcp.Stdio>(stdioTool)
        assertEquals("npx", stdioTool.command)
        assertEquals(listOf("-y", "@modelcontextprotocol/server-github"), stdioTool.args)

        // Verify agents are parsed correctly
        assertEquals(1, flowConfig.agents.size)
        assertEquals("greeter", flowConfig.agents[0].name)

        // Verify no transitions (single agent flow)
        assertEquals(0, flowConfig.transitions.size)
    }

    @Test
    fun testFlowParsing_withMcpSseToolWithHeaders() {
        val jsonContent = """
        {
            "id": "test-flow",
            "version": "1.0",
            "defaultModel": "openai/gpt4o",
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
            "defaultModel": "openai/gpt4o",
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
        val jsonContent = readFlow("json/verify_transform_flow.json")

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow config
        assertEquals("verify-transform-flow", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt4o", flowConfig.defaultModel)

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
        assertEquals("input.success", transition2.condition.variable)
        assertEquals(ConditionOperationKind.EQUALS, transition2.condition.operation)
        assertTrue(transition2.condition.value.isPrimitive)

        // verify_agent -> transform_feedback (condition: success == false)
        val transition3 = flowConfig.transitions[2]
        assertEquals("verify_agent", transition3.from)
        assertEquals("transform_feedback", transition3.to)
        assertNotNull(transition3.condition)
        assertEquals("input.success", transition3.condition.variable)
        assertEquals(ConditionOperationKind.EQUALS, transition3.condition.operation)

        // transform_feedback -> fix_agent (unconditional)
        val transition4 = flowConfig.transitions[3]
        assertEquals("transform_feedback", transition4.from)
        assertEquals("fix_agent", transition4.to)
        assertEquals(null, transition4.condition)
    }

    //endregion Verify and Transform

    //region ReAct

    @Test
    fun testJsonParsing_reactFlowJson() {
        val jsonContent = readFlow("json/react_flow.json")

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify flow config
        assertEquals("react-flow", flowConfig.id)
        assertEquals("1.0", flowConfig.version)
        assertEquals("openai/gpt4o", flowConfig.defaultModel)

        // Verify agents
        assertEquals(3, flowConfig.agents.size)

        // preprocessor
        val preprocessorAgent = flowConfig.agents[0]
        assertIs<FlowTaskAgent>(preprocessorAgent)
        assertEquals("preprocessor", preprocessorAgent.name)
        assertEquals(FlowAgentKind.TASK, preprocessorAgent.type)

        // react_problem_solver
        val reactAgent = flowConfig.agents[1]
        assertIs<FlowReActAgent>(reactAgent)
        assertEquals("react_problem_solver", reactAgent.name)
        assertEquals(FlowAgentKind.REACT, reactAgent.type)
        assertEquals("openai/gpt4o", reactAgent.model)
        assertNotNull(reactAgent.parameters)
        assertEquals(
            "Solve the problem using available tools. Reason about each step before taking action.",
            reactAgent.parameters.task
        )
        assertEquals(1, reactAgent.parameters.reasoningInterval)
        assertEquals(null, reactAgent.parameters.toolNames)

        // summarizer
        val summarizerAgent = flowConfig.agents[2]
        assertIs<FlowTaskAgent>(summarizerAgent)
        assertEquals("summarizer", summarizerAgent.name)
        assertEquals(FlowAgentKind.TASK, summarizerAgent.type)

        // Verify transitions
        assertEquals(2, flowConfig.transitions.size)

        val transition1 = flowConfig.transitions[0]
        assertEquals("preprocessor", transition1.from)
        assertEquals("react_problem_solver", transition1.to)

        val transition2 = flowConfig.transitions[1]
        assertEquals("react_problem_solver", transition2.from)
        assertEquals("summarizer", transition2.to)
    }

    //endregion ReAct

    //region Model Validation

    @Test
    fun testParserRejectsConfigWithoutModel() {
        val jsonContent = """
        {
            "id": "no-model-flow",
            "version": "1.0",
            "agents": [{
                "name": "test_agent",
                "type": "task",
                "params": {"task": "Do something"}
            }],
            "transitions": []
        }
        """.trimIndent()

        val parser = FlowJsonConfigParser()
        val exception = assertFailsWith<IllegalStateException> {
            parser.parse(jsonContent)
        }
        assertTrue(
            exception.message?.contains("Missing model name") == true,
            "Exception message should mention missing model name, but got: ${exception.message}"
        )
    }

    @Test
    fun testParserAcceptsConfigWithDefaultModel() {
        val jsonContent = """
        {
            "id": "with-default-model-flow",
            "version": "1.0",
            "defaultModel": "openai/gpt4o",
            "agents": [{
                "name": "test_agent",
                "type": "task",
                "params": {"task": "Do something"}
            }],
            "transitions": []
        }
        """.trimIndent()

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        assertNotNull(flowConfig)
        assertEquals(1, flowConfig.agents.size)
        assertEquals("openai/gpt4o", flowConfig.agents[0].model)
    }

    @Test
    fun testParserAcceptsConfigWithAgentSpecificModel() {
        val jsonContent = """
        {
            "id": "with-agent-model-flow",
            "version": "1.0",
            "agents": [{
                "name": "test_agent",
                "type": "task",
                "model": "ollama/meta/llama3.2:3b",
                "params": {"task": "Do something"}
            }],
            "transitions": []
        }
        """.trimIndent()

        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        assertNotNull(flowConfig)
        assertEquals(1, flowConfig.agents.size)
        assertEquals("ollama/meta/llama3.2:3b", flowConfig.agents[0].model)
    }

    //endregion Model Validation
}
