package ai.grazie.code.agents.example.simpleapi

import ai.grazie.code.agents.core.tools.SimpleToolRegistry
import ai.grazie.code.agents.local.simpleApi.simpleChatAgent
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val switch = Switch()

    /*
    *
    * You can also use the DSL to create a tool registry:
    *   val toolRegistry = SimpleToolRegistry {
    *       tool(SwitchTool(switch))
    *       tool(SwitchStateTool(switch))
    *   }
    * */
    val toolRegistry = SimpleToolRegistry {
        tools(
            listOf(
                SwitchTool(switch),
                SwitchStateTool(switch)
            )
        )
    }
    val agent = simpleChatAgent(
        executor = null!!,
        systemPrompt = "You're responsible for running a Switch and perform operations on it by request",
        cs = this,
        temperature = 0.0,
        toolRegistry = toolRegistry
    )
    println("Chat started")
    val input = readln()
    agent.run(input)
}