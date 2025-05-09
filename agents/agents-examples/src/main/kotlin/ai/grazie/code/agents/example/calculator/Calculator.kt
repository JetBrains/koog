package ai.grazie.code.agents.example.calculator

import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.api.AskUser
import ai.grazie.code.agents.core.api.SayToUser
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.*
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.example.TokenService
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

    // Create event handler for logging
    val eventHandler = EventHandler {
        onToolCall { stage, tool, args ->
            println("Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
        }

        handleError {
            println("An error occurred: ${it.message}\n${it.stackTraceToString()}")
            true
        }

        handleResult {
            println("Result: $it")
        }
    }

    // Create agent config with proper prompt
    val agentConfig = LocalAgentConfig(
        prompt = prompt(OpenAIModels.GPT4o, "test") {
            system("You are a calculator.")
        },
        maxAgentIterations = 50
    )

    // Create the runner
    val agent = AIAgentBase(
        promptExecutor = executor,
        strategy = strategy,
        cs = this,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        eventHandler = eventHandler
    )

    runBlocking {
        agent.run("(10 + 20) * (5 + 5) / (2 - 11)")
    }
}