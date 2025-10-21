package ai.koog.agents.example.attachments

import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.markdown.markdown
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path

suspend fun main() {
    val resourcePath =
        object {}.javaClass.classLoader.getResource("images")?.path ?: error("images directory not found")

    val prompt = prompt("example-prompt") {
        system("You are professional assistant that can write cool and funny descriptions for Instagram posts.")

        user {
            markdown {
                +"I want to create a new post on Instagram."
                br()
                +"Can you write something creative under my instagram post with the following photos?"
                br()
                h2("Requirements")
                bulleted {
                    item("It must be very funny and creative")
                    item("It must increase my chance of becoming an ultra-famous blogger!!!!")
                    item("It not contain explicit content, harassment or bullying")
                    item("It must be a short catching phrase")
                    item("You must include relevant hashtags that would increase the visibility of my post")
                }
            }

            attachments {
                image(Path("$resourcePath/photo1.png"))
                image(Path("$resourcePath/photo2.png"))
            }
        }
    }

    val openaiExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey)
    val anthropicExecutor = simpleAnthropicExecutor(ApiKeyService.anthropicApiKey)
    val googleExecutor = simpleGoogleAIExecutor(ApiKeyService.googleApiKey)

    try {
        println("OpenAI response:")
        openaiExecutor.execute(prompt, OpenAIModels.Chat.GPT4_1).single().content.also(::println)
        println("Anthropic response:")
        anthropicExecutor.execute(prompt, AnthropicModels.Sonnet_4).single().content.also(::println)
        println("Google response:")
        googleExecutor.execute(prompt, GoogleModels.Gemini2_0Flash).single().content.also(::println)
    } finally {
        openaiExecutor.close()
        anthropicExecutor.close()
        googleExecutor.close()
    }
}
