package com.jetbrains.example.koog.compose.agents.chat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import com.jetbrains.example.koog.compose.agents.common.AgentProvider

/**
 * Factory for creating chat agents
 */
internal class ChatAgentProvider(private val provideLLMClient: suspend () -> Pair<LLMClient, LLModel>) : AgentProvider {
    override val title: String = "Calculator"
    override val description: String = "Hi, I'm a calculator agent, I can do math"

    override suspend fun provideAgent(
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String,
    ): AIAgent<String, String> {
        val (llmClient, model) = provideLLMClient.invoke()
        val executor = SingleLLMPromptExecutor(llmClient = llmClient)

        // Create tool registry with just the exit tool
        val toolRegistry = ToolRegistry {
//            tool(ExitTool)
        }

        val strategy = strategy(title) {
            val nodeRequestLLM by nodeLLMRequest()
            val nodeAssistantMessage by node<String, String> { message -> onAssistantMessage(message) }

            edge(nodeStart forwardTo nodeRequestLLM)

            edge(
                nodeRequestLLM forwardTo nodeAssistantMessage
                    onAssistantMessage { true }
            )

            edge(
                nodeRequestLLM forwardTo nodeFinish
                    onToolCall { true }
                    transformed { it.content }
            )

            edge(nodeAssistantMessage forwardTo nodeRequestLLM)

        }

        // Create agent config with proper prompt
        val agentConfig = AIAgentConfig(
            prompt = prompt("chat") {
                system(
                    """
                    You are a helpful and friendly chat assistant.
                    Engage in conversation with the user, answering questions and providing information.
                    Be concise, accurate, and friendly in your responses.
                    If you don't know something, admit it rather than making up information.
                    """.trimIndent()
                )
            },
            model = model,
            maxAgentIterations = 50
        )

        // Create the runner
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
            }
        }
    }
}
