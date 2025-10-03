package com.jetbrains.example.kotlin_agents_demo_app.agents.chat

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
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.jetbrains.example.kotlin_agents_demo_app.agents.common.AgentProvider
import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettings
import kotlin.uuid.ExperimentalUuidApi

/**
 * Factory for creating chat agents
 */
class ChatAgentProvider(val defaultExecutor: PromptExecutor? = null, val defaultModel: LLModel? = null) : AgentProvider {
    override val title: String = "Chat"
    override val description: String = "Hi, I'm a chat agent, I can have a conversation with you"

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun provideAgent(
        appSettings: AppSettings,
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String,
    ): AIAgent<String, String> {
        val settings = appSettings.getCurrentSettings()
        val executor: PromptExecutor = defaultExecutor ?: run {
            val openAiToken = settings.openAiToken
            require(openAiToken.isNotEmpty()) { "OpenAI token is not configured." }
            simpleOpenAIExecutor(openAiToken)
        }

        val model = defaultModel ?: OpenAIModels.Chat.GPT4o

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
                onToolCall { ctx ->
                    onToolCallEvent("Tool ${ctx.tool.name}, args ${ctx.toolArgs}")
                }

                onAgentRunError { ctx ->
                    onErrorEvent("${ctx.throwable.message}")
                }

                onAgentFinished { ctx ->
                    // Skip finish event handling
                }
            }
        }
    }
}
