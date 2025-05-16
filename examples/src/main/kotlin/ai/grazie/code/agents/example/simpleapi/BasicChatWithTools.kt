package ai.grazie.code.agents.example.simpleapi

import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.ext.agent.simpleChatAgent
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
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
    val toolRegistry = ToolRegistry {
        tools(
            listOf(
                SwitchTool(switch),
                SwitchStateTool(switch)
            )
        )
    }
    val agent = simpleChatAgent(
        executor = simpleOpenAIExecutor(TokenService.openAIToken),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "You're responsible for running a Switch and perform operations on it by request",
        temperature = 0.0,
        toolRegistry = toolRegistry
    )
    println("Chat started")
    val input = readln()
    agent.run(input)
}