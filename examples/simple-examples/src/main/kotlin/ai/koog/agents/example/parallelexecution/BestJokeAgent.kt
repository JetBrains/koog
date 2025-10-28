package ai.koog.agents.example.parallelexecution

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import kotlinx.serialization.Serializable

@Serializable
@LLMDescription("The result of the best joke selection")
data class JokeWinner(
    @property:LLMDescription("Index of the winning joke from 0 to 2")
    val index: Int,
    @property:LLMDescription("The winning joke text")
    val jokeText: String
)

const val jokeSystemPrompt =
    "You are a comedian. Generate a funny joke about the given topic. Be creative and make it hilarious."
const val jokeCritiqueSystemPrompt = "You are a comedy critic. Give a critique for the given joke."

suspend fun main() {
    val openAIClient = OpenAILLMClient(ApiKeyService.openAIApiKey)
    val anthropicClient = AnthropicLLMClient(ApiKeyService.anthropicApiKey)

    // Create agent config
    val agentConfig = AIAgentConfig(
        prompt = prompt("best-joke-agent") {
            system("You are a joke generator that creates the best jokes about given topics.")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10
    )

    MultiLLMPromptExecutor(
        LLMProvider.OpenAI to openAIClient,
        LLMProvider.Anthropic to anthropicClient,
    ).use { executor ->

        // Create the agent
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = bestJokeStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(OpenTelemetry) {
                // Add a console logger for local debugging
                addSpanExporter(LoggingSpanExporter.create())
            }
        }

        val topic = "programming"
        println("Generating jokes about: $topic")

        // Run the agent
        val result = agent.run(topic)
        println("Final result: $result")
    }
}

fun bestJokeStrategy(): AIAgentGraphStrategy<String, String> = strategy("best-joke") {
    val nodeOpenAI by node<String, String> { topic ->
        requestAJoke(OpenAIModels.Chat.GPT4_1, topic)
    }

    val nodeAnthropicSonnet by node<String, String> { topic ->
        requestAJoke(AnthropicModels.Sonnet_3_5, topic)
    }

    val nodeAnthropicOpus by node<String, String> { topic ->
        requestAJoke(AnthropicModels.Opus_3, topic)
    }

    // Define a node to select the best joke
    val nodeGenerateBestJoke by parallel(
        nodeOpenAI,
        nodeAnthropicSonnet,
        nodeAnthropicOpus,
    ) {
        selectByIndex { jokes ->
            findTheBestJoke(jokes)
        }
    }

    // unused
    val nodeGenerateJokes by parallel(
        nodeOpenAI,
        nodeAnthropicSonnet,
        nodeAnthropicOpus,
    ) {
        fold("Jokes:\n") { result, joke -> "$result\n$joke" }
    }

    // unused
    val nodeGenerateLongestJoke by parallel(
        nodeOpenAI,
        nodeAnthropicSonnet,
        nodeAnthropicOpus,
    ) {
        selectByMax { it.length }
    }

    // unused
    val nodeGenerateJetbrainsJoke by parallel(
        nodeOpenAI,
        nodeAnthropicSonnet,
        nodeAnthropicOpus,
    ) {
        selectBy { it.contains("jetbrains") }
    }

    // Feel free to use `nodeGenerateJokes` or `nodeGenerateLongestJoke` or `nodeGenerateJetbrainsJoke` here:
    nodeStart then nodeGenerateBestJoke then nodeFinish
}

private suspend fun AIAgentContext.requestAJoke(
    model: LLModel,
    topic: String
): String = llm.writeSession {
    this.model = model
    rewritePrompt {
        prompt("best-joke-${model.id}") {
            system(jokeSystemPrompt)
            user("Tell me a joke about $topic.")
        }
    }
    val response = requestLLMWithoutTools()
    response.content
}

private suspend fun AIAgentContext.findTheBestJoke(
    jokes: List<String>
): Int = llm.writeSession {
    // Another LLM (ex: GPT4o) would find the funniest joke:
    model = OpenAIModels.Chat.GPT4o
    rewritePrompt {
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

    val response = requestLLMStructured<JokeWinner>()
    val bestJoke = response.getOrNull()!!.structure
    bestJoke.index
}
