package com.jetbrains.example.kotlin_agents_demo_app.agents.weather

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.jetbrains.example.kotlin_agents_demo_app.agents.common.AgentProvider
import com.jetbrains.example.kotlin_agents_demo_app.agents.common.ExitTool
import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettings

/**
 * Factory for creating weather forecast agents
 */
internal class WeatherAgentProvider : AgentProvider {
    override val title: String = "Weather Forecast"
    override val description: String = "Hi, I'm a weather agent. I can provide weather forecasts for any location."

    override suspend fun provideAgent(
        appSettings: AppSettings,
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String,
    ): AIAgent<String, String> {
        val openAiApiKey = appSettings.getCurrentSettings().openAiToken
        require(openAiApiKey.isNotEmpty()) { "OpenAI api key is not configured." }

        val executor = simpleOpenAIExecutor(openAiApiKey)

        // Create tool registry with weather tools
        val toolRegistry = ToolRegistry {
            tool(WeatherTools.CurrentDatetimeTool)
            tool(WeatherTools.AddDatetimeTool)
            tool(WeatherTools.WeatherForecastTool)

            tool(ExitTool)
        }

        @Suppress("DuplicatedCode")
        val strategy = strategy(title) {
            val nodeRequestLLM by nodeLLMRequestMultiple()
            val nodeAssistantMessage by node<String, String> { message -> onAssistantMessage(message) }
            val nodeExecuteToolMultiple by nodeExecuteMultipleTools(parallelTools = true)
            val nodeSendToolResultMultiple by nodeLLMSendMultipleToolResults()
            val nodeCompressHistory by nodeLLMCompressHistory<List<ReceivedToolResult>>()

            edge(nodeStart forwardTo nodeRequestLLM)

            edge(
                nodeRequestLLM forwardTo nodeExecuteToolMultiple
                    onMultipleToolCalls { true }
            )

            edge(
                nodeRequestLLM forwardTo nodeAssistantMessage
                    transformed { it.first() }
                    onAssistantMessage { true }
            )

            edge(nodeAssistantMessage forwardTo nodeRequestLLM)

            // Finish condition - if exit tool is called, go to nodeFinish with tool call result.
            edge(
                nodeExecuteToolMultiple forwardTo nodeFinish
                    onCondition { it.singleOrNull()?.tool == ExitTool.name }
                    transformed { it.single().result!!.toStringDefault() }
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
                nodeSendToolResultMultiple forwardTo nodeAssistantMessage
                    transformed { it.first() }
                    onAssistantMessage { true }
            )

        }

        // Create agent config with proper prompt
        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system(
                    """
                    You are a helpful weather assistant.
                    You can provide weather forecasts for any location in the world and help the user plan their activities.
                    
                    Use the tools at your disposal to:
                    1. Get the current date and time
                    2. Add days, hours, or minutes to a date
                    3. Get weather forecasts for specific locations and dates
                    
                    ALWAYS USE current_datetime and add_datetime tools to perform date operations, do not try to guess.
                    
                    When providing weather forecasts, be helpful and informative, explaining the weather conditions in a clear way.
                    """.trimIndent()
                )
            },
            model = OpenAIModels.Chat.GPT4o,
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
