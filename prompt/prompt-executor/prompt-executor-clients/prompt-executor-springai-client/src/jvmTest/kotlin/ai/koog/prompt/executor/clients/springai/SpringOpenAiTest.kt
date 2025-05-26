package ai.koog.prompt.executor.clients.springai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.kpavlov.aimocks.openai.MockOpenai
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.api.OpenAiApi
import kotlin.test.Test

private val mockOpenai = MockOpenai(verbose = true)

internal class SpringOpenAiTest {

    private val chatClient = ChatClient.builder(
        OpenAiChatModel
            .builder()
            .openAiApi(
                OpenAiApi
                    .builder()
                    .apiKey("demo-key")
                    .baseUrl("http://127.0.0.1:${mockOpenai.port()}")
                    .build(),
            ).build(),
    ).build()

    private val subject = SpringAiLLMClient(chatClient)

    @Test
    fun `Should execute completion request`() = runTest {
        // Given
        mockOpenai.completion {
            model("gpt-4.1-mini")
            systemMessageContains("helpful pirate")
            userMessageContains("say 'Hello!'")
        } responds {
            assistantContent = "Ahoy there, matey! Hello!"
            finishReason = "stop"
        }

        val prompt = Prompt.build("clientRequest") {
            system("You are a helpful pirate")
            user("Just say 'Hello!'")
        }
        val model = LLModel(
            LLMProvider.OpenAI, "gpt-4.1-mini", listOf(
                LLMCapability.Completion,
            )
        )
        // when
        val responses = subject.execute(prompt, model)

        responses.first() shouldNotBeNull {
            content shouldBe "Ahoy there, matey! Hello!"
        }
    }

    @Test
    fun `Should execute stream completion request`() = runTest {
        // Given
        mockOpenai.completion {
            model("gpt-4.1-nano")
            systemMessageContains("angry pirate")
            userMessageContains("say 'Ahoy!'")
        } respondsStream {
            responseFlow = flow {
                emit("Ahoy")
                emit("there!")
            }
        }

        val prompt = Prompt.build("clientRequest") {
            system("You are a angry pirate")
            user("Just say 'Ahoy!'")
        }
        val model = LLModel(
            LLMProvider.OpenAI, "gpt-4.1-nano", listOf(
                LLMCapability.Completion,
            )
        )
        // when
        val responseFlow = subject.executeStreaming(prompt, model)

        val resultList = mutableListOf<String>()
        responseFlow.toList(resultList)
        resultList.joinToString(separator = " ") shouldBe " Ahoy there!"
    }
}
