package ai.koog.agents.example.simpleapi

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

suspend fun main() {
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
        tools(SwitchTools(switch).asTools())
    }

    simpleOpenAIExecutor(ApiKeyService.openAIApiKey).use { executor ->
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You're responsible for running a Switch and perform operations on it by request",
            temperature = 0.0,
            toolRegistry = toolRegistry
        )
        println("Chat started")
        val input = readln()
        agent.run(input)
    }
}
