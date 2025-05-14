package ai.grazie.code.agents.example.calculator

import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.*
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.tools.AskUser
import ai.grazie.code.agents.core.tools.tools.SayToUser
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.features.eventHandler.feature.handleEvents
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(TokenService.openAIToken)
    val calculatorStageName = "calculator"

    // Create tool registry with calculator tools
    val toolRegistry = ToolRegistry {
        stage(calculatorStageName) {
            // Special tool, required with this type of agent.
            tool(AskUser)
            tool(SayToUser)
            with(CalculatorTools) {
                tools()
            }
        }
    }

    val strategy = strategy("test") {
        stage(
            name = calculatorStageName,
            requiredTools = toolRegistry.stagesToolDescriptors.getValue(calculatorStageName)
        ) {
            val nodeSendInput by nodeLLMSendStageInputMultiple()
            val nodeExecuteToolMultiple by nodeExecuteMultipleTools(parallelTools = true)
            val nodeSendToolResultMultiple by nodeLLMSendMultipleToolResults()
            val nodeCompressHistory by nodeLLMCompressHistory<List<ReceivedToolResult>>()

            edge(nodeStart forwardTo nodeSendInput)

            edge(
                (nodeSendInput forwardTo nodeFinish)
                        transformed { it.first() }
                    onAssistantMessage { true }
            )

            edge(
                (nodeSendInput forwardTo nodeExecuteToolMultiple)
                    onMultipleToolCalls { true }
            )

            edge(
                (nodeExecuteToolMultiple forwardTo nodeCompressHistory)
                        onCondition { _ -> llm.readSession { prompt.messages.size > 100 } }
            )

            edge(nodeCompressHistory forwardTo nodeSendToolResultMultiple)

            edge(
                (nodeExecuteToolMultiple forwardTo nodeSendToolResultMultiple)
                        onCondition { _ -> llm.readSession { prompt.messages.size <= 100 } }
            )

            edge(
                (nodeSendToolResultMultiple forwardTo nodeExecuteToolMultiple)
                        onMultipleToolCalls { true }
            )

            edge(
                (nodeSendToolResultMultiple forwardTo nodeFinish)
                        transformed { it.first() }
                        onAssistantMessage { true }
            )

        }
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
        strategy = strategy,
        cs = this,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry
    ) {
        handleEvents {
            onToolCall = { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args ->
                println("Tool called: stage ${stage.name}, tool ${tool.name}, args $toolArgs")
            }

            onAgentRunError = { strategyName: String, throwable: Throwable ->
                println("An error occurred: ${throwable.message}\n${throwable.stackTraceToString()}")
            }

            onAgentFinished = { strategyName: String, result: String? ->
                println("Result: $result")
            }
        }
    }

    runBlocking {
        agent.run("(10 + 20) * (5 + 5) / (2 - 11)")
    }
}