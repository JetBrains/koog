package com.jetbrains.example.koog.compose.agents.calculator

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import com.jetbrains.example.koog.compose.agents.common.AgentProvider
import com.jetbrains.example.koog.compose.agents.common.ExitTool

/**
 * Factory for creating calculator agents
 */
internal class CalculatorAgentProvider(private val provideLLMClient: suspend () -> Pair<LLMClient, LLModel>) : AgentProvider {
    override val title: String = "Calculator"
    override val description: String = "Hi, I'm a calculator agent, I can do math"

    override suspend fun provideAgent(
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String,
    ): AIAgent<String, String> {
        val (llmClient, model) = provideLLMClient.invoke()
        val executor = MultiLLMPromptExecutor(llmClient)

        val toolRegistry = ToolRegistry {
            tool(CalculatorTool.PlusTool)
            tool(CalculatorTool.MinusTool)
            tool(CalculatorTool.DivideTool)
            tool(CalculatorTool.MultiplyTool)

            tool(ExitTool)
        }

        @Suppress("DuplicatedCode")
        val strategy = strategy<String, String>(title) {
            val nodeRequestLLM by nodeLLMRequest()
            val nodeAssistantMessage by node<String, String> { message -> onAssistantMessage(message) }
            val nodeExecuteToolMultiple by nodeExecuteTools(parallel = true)
            val nodeSendToolResultMultiple by nodeLLMSendToolResults()
            val nodeCompressHistory by nodeLLMCompressHistory<ReceivedToolResults>()

            edge(nodeStart forwardTo nodeRequestLLM)

            edge(
                nodeRequestLLM forwardTo nodeExecuteToolMultiple
                    onToolCalls { true }
            )

            edge(
                nodeRequestLLM forwardTo nodeAssistantMessage
                    onTextMessage { true }
            )

            edge(nodeAssistantMessage forwardTo nodeRequestLLM)

            // Finish condition - if exit tool is called, go to nodeFinish with tool call result.
            edge(
                nodeExecuteToolMultiple forwardTo nodeFinish
                    onCondition { it.toolResults.singleOrNull()?.tool == ExitTool.name }
                    transformed { it.toolResults.single().output }
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
                    onToolCalls { true }
            )

            edge(
                nodeSendToolResultMultiple forwardTo nodeAssistantMessage
                    onTextMessage { true }
            )
        }

        // Create agent config with proper prompt
        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system(
                    """
                    You are a calculator.
                    You will be provided math problems by the user.
                    Use tools at your disposal to solve them.
                    Provide the answer and ask for the next problem until the user asks to stop.
                    """.trimIndent()
                )
            },
            model = model,
            maxAgentIterations = 50
        )

        // Return the agent
        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            handleEvents {
                onToolCallStarting { ctx ->
                    onToolCallEvent("Tool ${ctx.toolName}, args ${ctx.toolArgs}")
                }

                onAgentExecutionFailed { ctx ->
                    onErrorEvent("${ctx.error.message}")
                }

                onAgentCompleted { _ ->
                    // Skip finish event handling
                }
            }
        }
    }
}
