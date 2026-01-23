package ai.koog

import ai.koog.config.parser.FlowJsonConfigParser
import ai.koog.flow.FlowConfig
import ai.koog.flow.SimpleFlow
import kotlinx.coroutines.runBlocking

/**
 * Example main function to demonstrate flow parsing and execution.
 */
public fun main(): Unit = runBlocking {
    val jsonContent = readFlow()

    val parser = FlowJsonConfigParser()
    val config: FlowConfig = parser.parse(jsonContent)

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
    println("\nCreating SimpleFlow and running...")
    val flow = SimpleFlow(
        id = config.id ?: "simple-flow",
        agents = config.agents,
        tools = emptyList(),
        transitions = config.transitions
    )

    val result = flow.run()
    println("Flow execution result: $result")
}

private fun readFlow(): String {
    val jsonContent = object {}.javaClass
//        .getResourceAsStream("/flow/simple_koog_agent_flow.json")
        .getResourceAsStream("/flow/random_koog_agent_flow.json")
        ?.bufferedReader()
        ?.readText()
        ?: error("Could not read JSON file")

    return jsonContent
}
