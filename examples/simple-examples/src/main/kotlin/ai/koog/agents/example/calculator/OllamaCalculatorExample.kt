package ai.koog.agents.example.calculator

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val executor: PromptExecutor = simpleOllamaAIExecutor()

    // Create a tool registry with calculator tools
    val toolRegistry = ToolRegistry {
        // Special tool, required with this type of agent.
        tool(AskUser)
        tool(SayToUser)
        tools(CalculatorTools())
    }

    // Create agent config with a proper prompt
    val agentConfig = AIAgentConfig(
        prompt = prompt("test", LLMParams(temperature = 0.0)) {
            system("You are a calculator.")
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 50
    )

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
        agent.run("Use provided tools to calculate (10 + 20) * (5 + 5) / (2 - 11). Please call all the tools at once")
    }
}
