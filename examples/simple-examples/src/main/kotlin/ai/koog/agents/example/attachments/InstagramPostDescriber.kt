package ai.koog.agents.example.attachments

import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import kotlinx.io.files.Path

private fun Message.Assistant.textContent(): String =
    parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }

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

            image(Path("$resourcePath/photo1.png"))
            image(Path("$resourcePath/photo2.png"))
        }
    }

    val ollamaModel = OllamaModels.Alibaba.QWEN_3_5_9B
    val llmClient = OllamaClient().also { it.getModelOrNull(ollamaModel.id, pullIfMissing = true) }
    val ollamaExecutor = MultiLLMPromptExecutor(llmClient)
    val openaiExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey)
    val anthropicExecutor = simpleAnthropicExecutor(ApiKeyService.anthropicApiKey)
    val googleExecutor = simpleGoogleAIExecutor(ApiKeyService.googleApiKey)

    try {
        println("OllamaAI response:")
        ollamaExecutor.execute(prompt, ollamaModel).textContent().also(::println)
        println("OpenAI response:")
        openaiExecutor.execute(prompt, OpenAIModels.Chat.GPT4_1).textContent().also(::println)
        println("Anthropic response:")
        anthropicExecutor.execute(prompt, AnthropicModels.Sonnet_4).textContent().also(::println)
        println("Google response:")
        googleExecutor.execute(prompt, GoogleModels.Gemini2_5Flash).textContent().also(::println)
    } finally {
        ollamaExecutor.close()
        openaiExecutor.close()
        anthropicExecutor.close()
        googleExecutor.close()
    }
}
