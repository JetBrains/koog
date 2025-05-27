package ai.koog.prompt.executor.clients.springai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.test.runTest
import me.kpavlov.aimocks.openai.ChatCompletionRequest
import me.kpavlov.aimocks.openai.MockOpenai
import me.kpavlov.aimocks.openai.completions.OpenaiChatCompletionRequestSpecification
import me.kpavlov.aimocks.openai.model.ChatCompletionRole
import org.junit.jupiter.api.BeforeEach
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

    private lateinit var prompt: Prompt

    private val subject = SpringAiLLMClient(chatClient)

    @BeforeEach
    fun setUp() {
        prompt = Prompt.build("clientRequest") {
            system("You are a angry pirate. Include today's weather in your response.")
            user("Just say 'Ahoy!'")
            assistant("I need to know what the weather will be like on Nassau")
            tool {
                call(
                    Message.Tool.Call(
                        id = "weather",
                        tool = "get_weather",
                        "What is the weather on Nassau?"
                    )
                )
                call(
                    Message.Tool.Call(
                        id = "time",
                        tool = "get_time",
                        "What time is it in Nassau now?"
                    )
                )
            }
            tool {
                result(
                    Message.Tool.Result(
                        id = "weather",
                        tool = "get_weather",
                        "It's sunny"
                    )
                )
            }
            tool {
                result(
                    Message.Tool.Result(
                        id = "time",
                        tool = "get_time",
                        "It's 9:42AM"
                    )
                )
            }
        }
    }

    @Test
    fun `Should execute completion request`() = runTest {
        // Given
        mockOpenai.completion {
            requestMatched("gpt-4.1-mini")
        } responds {
            assistantContent = "Ahoy there from sunny Nassau! Hello!"
            finishReason = "stop"
        }

        val model = LLModel(
            LLMProvider.OpenAI, "gpt-4.1-mini", listOf(
                LLMCapability.Completion,
            )
        )
        // when
        val responses = subject.execute(prompt, model)

        responses.first() shouldNotBeNull {
            content shouldBe "Ahoy there from sunny Nassau! Hello!"
        }
    }

    @Test
    fun `Should execute stream completion request`() = runTest {
        // Given
        mockOpenai.completion {
            requestMatched("gpt-4.1-nano")
        } respondsStream {
            responseFlow = "Ahoy there from sunny Nassau! Hello!"
                .split(" ")
                .asFlow()
                .transform {
                    delay(42)
                    emit(it)
                }
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
        resultList.joinToString(separator = " ") shouldBe " Ahoy there from sunny Nassau! Hello!"
    }

    private fun OpenaiChatCompletionRequestSpecification.requestMatched(modelName: String) {
        model(modelName)
        systemMessageContains(prompt.messages.first { it is Message.System }.content)
        userMessageContains(prompt.messages.first { it is Message.User }.content)
        requestBodyContains("What is the weather on Nassau?")
        requestBodyContains("It's sunny")
        requestBody.add(requestBodyMatcher { it.messages.size == 7 })
        requestBody.add(requestBodyMatcher(::toolCallsPredicate))
        requestBody.add(requestBodyMatcher(::toolResultPredicate))
    }

    private fun toolCallsPredicate(request: ChatCompletionRequest): Boolean {
        if (request.messages.size != 7) return false

        val toolCalls = request.messages.filter { message ->
            message.toolCalls?.isNotEmpty() == true
        }.map { it.toolCalls!! }.flatten()

        toolCalls.firstOrNull() {
            it.id == "weather" &&
                    it.type == "function" &&
                    it.function.name == "get_weather" &&
                    it.function.arguments == "What is the weather on Nassau?"
        } ?: return false

        toolCalls.firstOrNull() {
            it.id == "time" &&
                    it.type == "function" &&
                    it.function.name == "get_time" &&
                    it.function.arguments == "What time is it in Nassau now?"
        } ?: return false

        return true
    }

    private fun toolResultPredicate(request: ChatCompletionRequest): Boolean {
        val toolResults = request.messages.filter { message ->
            message.role == ChatCompletionRole.TOOL
        }

        toolResults.firstOrNull() {
            it.content == "It's sunny"
        } ?: return false

        toolResults.firstOrNull() {
            it.content == "It's 9:42AM"
        } ?: return false

        return true
    }

    private fun requestBodyMatcher(predicate: (ChatCompletionRequest) -> Boolean): Matcher<ChatCompletionRequest?> =
        object : Matcher<ChatCompletionRequest?> {
            override fun test(value: ChatCompletionRequest?): MatcherResult {
                val passed = value != null && predicate(value!!)
                return MatcherResult.Companion(
                    passed,
                    { "Request should satisfy predicate. Request: $value" },
                    { "Request should not satisfy predicate. Request: $value" },
                )
            }
        }
}
