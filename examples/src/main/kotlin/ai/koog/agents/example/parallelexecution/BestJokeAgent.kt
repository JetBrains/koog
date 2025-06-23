package ai.koog.agents.example.parallelexecution

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.NodeExecutionResult
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.structure.json.JsonStructuredData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable


@Serializable
@LLMDescription("The result of the best joke selection")
data class JokeWinner(
    @LLMDescription("Index of the winning joke from 0 to 2")
    val index: Int,
    @LLMDescription("The winning joke text")
    val jokeText: String
)


fun main(args: Array<String>) = runBlocking {

    val jokeSystemPrompt =
        "You are a comedian. Generate a funny joke about the given topic. Be creative and make it hilarious."
    val jokeCritiqueSystemPrompt = "You are a comedy critic. Give a critique for the given joke."

    val strategy = strategy("best-joke") {
        val nodeOpenAI by node<String, String> { topic ->
            llm.writeSession {
                model = OpenAIModels.Chat.GPT4o
                updatePrompt {
                    system(jokeSystemPrompt)
                    user("Tell me a joke about $topic.")
                }
                val response = requestLLMWithoutTools()
                response.content
            }
        }

        val nodeAnthropicSonnet by node<String, String> { topic ->
            llm.writeSession {
                model = AnthropicModels.Sonnet_3_5
                updatePrompt {
                    system(jokeSystemPrompt)
                    user("Tell me a joke about $topic.")
                }
                val response = requestLLMWithoutTools()
                response.content
            }
        }

        val nodeAnthropicOpus by node<String, String> { topic ->
            llm.writeSession {
                model = AnthropicModels.Opus_3
                updatePrompt {
                    system(jokeSystemPrompt)
                    user("Tell me a joke about $topic.")
                }
                val response = requestLLMWithoutTools()
                response.content
            }
        }

        // Define a node to select the best joke
        val nodeGenerateJokes by parallel(
            nodeOpenAI, nodeAnthropicSonnet, nodeAnthropicOpus,
        )

        val nodeTransformJoke by transform<String, String, String> { joke ->
            "My favorite joke: $joke"
        }

        val nodeSelectBestJoke by merge<String, String> {
            selectByIndex { jokes ->
                // Another LLM (ex: GPT4o) would find the funniest joke:
                llm.writeSession {
                    model = OpenAIModels.Chat.GPT4o
                    updatePrompt {
                        prompt("best-joke-selector") {
                            system(jokeCritiqueSystemPrompt)
                            user(
                                """
                                Here are three jokes about the same topic:

                                ${jokes.mapIndexed { index, joke -> "Joke $index:\n$joke" }.joinToString("\n\n")}

                                Select the best joke and explain why it's the best.
                            """.trimIndent()
                            )
                        }
                    }

                    val response = requestLLMStructured(JsonStructuredData.createJsonStructure<JokeWinner>())
                    val bestJoke = response.getOrNull()!!.structure
                    bestJoke.index
                }
            }
        }

        // unused
        val concatenateJokes by merge<String, String> {
            fold("Jokes:\n") { result, joke -> "$result\n$joke" }
        }

        // unused
        val longestJoke by merge<String, String> {
            selectByMax { it.length }
        }

        // unused
        val jokeContainingJetBrains by merge<String, String> {
            selectBy { it.contains("jetbrains") }
        }

        // Feel free to use `concatenateJokes` or `longestJoke` or `jokeContainingJetBrains` here:
        nodeStart then nodeGenerateJokes then nodeTransformJoke then nodeSelectBestJoke then nodeFinish
    }

    // Create agent config
    val agentConfig = AIAgentConfig(
        prompt = prompt("best-joke-agent") {
            system("You are a joke generator that creates the best jokes about given topics.")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10
    )

    // Create the agent
    val agent = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to OpenAILLMClient(ApiKeyService.openAIApiKey),
            LLMProvider.Anthropic to AnthropicLLMClient(ApiKeyService.anthropicApiKey),
        ),
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry.EMPTY
    ) {

    }

    val topic = "programming"
    println("Generating jokes about: $topic")

    // Run the agent
    val result = agent.run(topic)
    println("Final result: $result")
}
