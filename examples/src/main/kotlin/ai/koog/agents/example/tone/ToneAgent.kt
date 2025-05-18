package ai.koog.agents.example.tone

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.TokenService
import ai.koog.agents.example.tone.ToneTools.NegativeToneTool
import ai.koog.agents.example.tone.ToneTools.NeutralToneTool
import ai.koog.agents.example.tone.ToneTools.PositiveToneTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.local.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking

fun main() {
    val executor: PromptExecutor = simpleOpenAIExecutor(TokenService.openAIToken)

    /**
     * Describe the list of tools for your agent.
     */
    val toolRegistry = ToolRegistry {
        tool(SayToUser)
        tool(PositiveToneTool)
        tool(NegativeToneTool)
        tool(NeutralToneTool)
    }

    runBlocking {
        println()
        println("I am agent that can answer question and analyze tone. Enter your sentence: ")
        val userRequest = readln()

        // Create agent config with a proper prompt
        val agentConfig = AIAgentConfig(
            prompt = prompt("tone_analysis") {
                system(
                    """
                    You are an question answering agent with access to the tone analysis tools.
                    You need to answer 1 question with the best of your ability.
                    Be as concise as possible in your answers, and only return the tone in your final answer.
                    Do not apply any locale-specific formatting to the result.
                    DO NOT ANSWER ANY QUESTIONS THAT ARE BESIDES PERFORMING TONE ANALYSIS!
                    DO NOT HALLUCINATE!
                    """.trimIndent()
                )
            },
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 10
        )

        // Create the strategy
        val strategy = toneStrategy("tone_analysis")

        // Create the agent
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            handleEvents {
                onToolCall = { tool: Tool<*, *>, toolArgs: Tool.Args ->
                    println("Tool called: tool ${tool.name}, args $toolArgs")
                }

                onAgentRunError = { strategyName: String, throwable: Throwable ->
                    println("An error occurred: ${throwable.message}\n${throwable.stackTraceToString()}")
                }

                onAgentFinished = { strategyName: String, result: String? ->
                    println("Result: $result")
                }
            }
        }

        agent.run(userRequest)
    }
}
