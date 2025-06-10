package ai.koog.agents.example.parallelexecution

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.structure.json.JsonSchemaGenerator
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
                model = AnthropicModels.Opus
                updatePrompt {
                    system(jokeSystemPrompt)
                    user("Tell me a joke about $topic.")
                }
                val response = requestLLMWithoutTools()
                response.content
            }
        }

        // Define a node to select the best joke
        val nodeBestJoke by parallel(
            nodes = listOf(nodeOpenAI, nodeAnthropicSonnet, nodeAnthropicOpus),
            reduce = { results ->
                val context = results.map { it.second }
                val jokes = results.map { it.third }

                val bestJokeIndex = this.llm.writeSession {
                    model = OpenAIModels.Chat.GPT4o
                    updatePrompt {
                        prompt("best-joke-selector") {
                            system(jokeCritiqueSystemPrompt)
                            user(
                                """
                                Here are three jokes about the same topic:

                                ${jokes.mapIndexed { index, joke -> "Joke $index:\n$joke"  }.joinToString("\n\n")}

                                Select the best joke and explain why it's the best.
                            """.trimIndent()
                            )
                        }
                    }


                    val response = requestLLMStructured(
                        structure = JsonStructuredData.createJsonStructure<JokeWinner>(
                            schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
                            schemaType = JsonStructuredData.JsonSchemaType.FULL
                        )
                    )
                    val bestJoke = response.getOrNull()!!.structure
                    bestJoke.index
                }

                context[bestJokeIndex] to jokes[bestJokeIndex]

            }
        )

        nodeStart then nodeBestJoke then nodeFinish
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
        handleEvents {
            onAgentRunError = { strategyName: String, throwable: Throwable ->
                println("An error occurred: ${throwable.message}\n${throwable.stackTraceToString()}")
            }

            onAgentFinished = { strategyName: String, result: String? ->
                println("Result: $result")
            }

            onBeforeLLMCall = { prompt, tools ->
                println("Before LLM call: $prompt")
            }
        }
    }

    val topic = "programming"
    println("Generating jokes about: $topic")

    // Run the agent
    val result = agent.run(topic)
    println("Final result: $result")
}
