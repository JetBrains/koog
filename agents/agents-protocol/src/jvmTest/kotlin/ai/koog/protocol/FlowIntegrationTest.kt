package ai.koog.protocol

import ai.koog.protocol.flow.KoogFlow
import ai.koog.protocol.parser.FlowJsonConfigParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class FlowIntegrationTest : FlowTestBase() {

    @Test
    fun testRealAgent(): Unit = runBlocking {
        val jsonContent = readFlow("real_koog_agent_flow.json")

        val parser = FlowJsonConfigParser()
        val config = parser.parse(jsonContent)

        println("Parsed FlowConfig:")
        println("  ID: ${config.id}")
        println("  Version: ${config.version}")
        println("  Agents: ${config.agents.size}")
        config.agents.forEach { agent ->
            println("    - ${agent.name} (${agent.type})")
        }
        println("  Transitions: ${config.transitions.size}")
        config.transitions.forEach { transition ->
            println("    - ${transition.from} -> ${transition.to}")
        }

        // Create a SimpleFlow from the config and run it
        println("\nCreating a flow and running...")
        val flow = KoogFlow(
            id = config.id ?: "simple-flow",
            agents = config.agents,
            tools = config.tools,
            transitions = config.transitions
        )

        val result = flow.run(null)
        println("Flow execution result: $result")
    }
}
