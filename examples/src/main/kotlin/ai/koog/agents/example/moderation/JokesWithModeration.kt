package ai.koog.agents.example.moderation

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ModeratedMessage
import ai.koog.agents.core.dsl.extension.nodeLLMModerateMessage
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking


/**
 * - Asks the user to input a topic for a joke.
 * - Uses a chat agent with OpenAI's GPT-4 model to generate jokes based on user input.
 * - Moderates the generated content to identify and handle potentially harmful jokes.
 * - Implements a mechanism to regenerate jokes until they are deemed non-harmful.
 */
fun main() = runBlocking {
    val moderatingStrategy = strategy("sage-joke-gen") {
        val callLLM: AIAgentNodeBase<String, Message.Response> by nodeLLMRequest()
        val moderate: AIAgentNodeBase<Message, ModeratedMessage> by nodeLLMModerateMessage(moderatingModel = OpenAIModels.Moderation.Omni)

        // Check that the initial topic is harmful from the beginning
        edge(nodeStart forwardTo nodeFinish onCondition {
            llm.readSession {
                requestModeration(
                    moderatingModel = OpenAIModels.Moderation.Omni
                ).isHarmful
            }
        } transformed { "You have requested harmful content. Sorry, I can't generate a joke for you." })

        edge(nodeStart forwardTo callLLM)
        // Moderate every joke
        edge(callLLM forwardTo moderate)
        // Accept good jokes
        edge(moderate forwardTo nodeFinish onCondition { !it.moderationResult.isHarmful } transformed { it.message.content })
        // Give feedback to re-generate joke if it's harmful
        edge(moderate forwardTo callLLM onCondition { it.moderationResult.isHarmful } transformed { moderatedMessage ->
            markdown {
                h1("You must re-generate the joke to make it not harmful")

                text("The following moderation categories were detected: ")
                bulleted {
                    moderatedMessage.moderationResult.violatedCategories.forEach { category ->
                        // Tell specifically what moderation categories were violated
                        item(category.name)
                    }
                }

            }
        })
    }

    // Create a chat agent with a system prompt for joke generation
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = """
            You are professional joke generator. Generate a funny joke based on the user's input.
        """.trimIndent(),
        temperature = 0.0
    )

    println("Type the topic for the joke and press Enter to generate a joke.")
    val initialMessage = readln()

    val result = agent.run(initialMessage)
    println(result)
}