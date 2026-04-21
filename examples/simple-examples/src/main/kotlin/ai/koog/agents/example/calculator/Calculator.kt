package ai.koog.agents.example.calculator

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel

private enum class RunMode {
    LOCAL_LLAMA_3_2,
    OPEN_AI_GPT_4o
}

/**
 * Demonstrates how to build a graph-based calculator agent in Kotlin using Koog APIs:
 * - Defining tools via [CalculatorTools]
 * - Building a graph strategy with multiple nodes and typed edges
 * - Handling agent events via `handleEvents`
 *
 * Usage:
 * - Run with OpenAI GPT-4o (requires `OPENAI_API_KEY`): `./gradlew runExampleCalculator`
 * - Run with local Ollama Llama 3.2: `./gradlew runExampleCalculatorLocal`
 */
suspend fun main(args: Array<String>) {
    val runMode = when (args.firstOrNull()) {
        "local" -> RunMode.LOCAL_LLAMA_3_2
        else -> RunMode.OPEN_AI_GPT_4o
    }

    runCalculatorExample(runMode)
}

private suspend fun runCalculatorExample(runMode: RunMode) {
    val executor = chooseExecutor(runMode)
    val model = chooseModel(runMode)

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
        model = model,
        maxAgentIterations = 50
    )

    executor.use { promptExecutor ->
        // Create the runner
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            strategy = CalculatorStrategy.strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            handleEvents {
                onToolCallStarting { eventContext ->
                    println("Tool called: tool ${eventContext.toolName}, args ${eventContext.toolArgs}")
                }

                onAgentExecutionFailed { eventContext ->
                    println(
                        "An error occurred: ${eventContext.throwable.message}\n${eventContext.throwable.stackTraceToString()}"
                    )
                }

                onAgentCompleted { eventContext ->
                    println("Result: ${eventContext.result}")
                }
            }
        }

        val result = agent.run("(10 + 20) * (5 + 5) / (2 - 11)")
        println("Agent result: $result")
    }
}

private fun chooseExecutor(mode: RunMode): PromptExecutor = when (mode) {
    RunMode.LOCAL_LLAMA_3_2 -> simpleOllamaAIExecutor("http://localhost:11434")
    RunMode.OPEN_AI_GPT_4o -> simpleOpenAIExecutor(ApiKeyService.openAIApiKey)
}

private fun chooseModel(mode: RunMode): LLModel = when (mode) {
    RunMode.LOCAL_LLAMA_3_2 -> OllamaModels.Meta.LLAMA_3_2
    RunMode.OPEN_AI_GPT_4o -> OpenAIModels.Chat.GPT4o
}
