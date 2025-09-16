package ai.koog.agents.example.calculator

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey)

    // Create a tool registry with calculator tools
    val toolRegistry = ToolRegistry {
        // Special tool, required with this type of agent.
        tool(AskUser)
        tool(SayToUser)
        tools(CalculatorTools().asTools())
    }

    // Create agent config with proper prompt
    val agentConfig = AIAgentConfig(
        prompt = prompt("test") {
            system("You are a calculator.")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 50
    )

    // Create the runner
    val agent = AIAgent(
        promptExecutor = executor,
        strategy = CalculatorStrategy.strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry
    ) {
        handleEvents {
            onToolCall { eventContext ->
                println("Tool called: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}")
            }

            onAgentRunError { eventContext ->
                println(
                    "An error occurred: ${eventContext.throwable.message}\n${eventContext.throwable.stackTraceToString()}"
                )
            }

            onAgentFinished { eventContext ->
                println("Result: ${eventContext.result}")
            }
        }
    }

    runBlocking {
        agent.run("(10 + 20) * (5 + 5) / (2 - 11)")
    }
}
