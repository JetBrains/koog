package ai.grazie.code.agents.example.tone

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.example.tone.ToneTools.NegativeToneTool
import ai.grazie.code.agents.example.tone.ToneTools.NeutralToneTool
import ai.grazie.code.agents.example.tone.ToneTools.PositiveToneTool
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.simpleApi.SayToUser
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking

fun main() {
    val executor: PromptExecutor = simpleOpenAIExecutor(TokenService.openAIToken)

    /**
     * Describe the list of tools for your agent.
     */
    val toneStageName = "tone_analysis"

    /**
     * Describe the list of tools for your agent.
     */
    val toolRegistry = ToolRegistry {
        stage(toneStageName) {
            tool(SayToUser)
            tool(PositiveToneTool)
            tool(NegativeToneTool)
            tool(NeutralToneTool)
        }
    }

    runBlocking {
        println()
        println("I am agent that can answer question and analyze tone. Enter your sentence: ")
        val userRequest = readln()

        // Create an event handler for logging
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

        // Create agent config with a proper prompt
        val agentConfig = LocalAgentConfig(
            prompt = prompt(OpenAIModels.GPT4o, "tone_analysis") {
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
            maxAgentIterations = 10
        )

        // Get token from environment or use a default for tests
        val token = System.getenv("GRAZIE_TOKEN") ?: "test-token"

        // Create the strategy
        val strategy = toneStrategy("tone_analysis", toolRegistry, toneStageName)

        // Create the agent
        val agent = KotlinAIAgent(
            toolRegistry = toolRegistry,
            strategy = strategy,
            eventHandler = eventHandler,
            agentConfig = agentConfig,
            promptExecutor = executor,
            cs = this
        )

        agent.run(userRequest)
    }
}
