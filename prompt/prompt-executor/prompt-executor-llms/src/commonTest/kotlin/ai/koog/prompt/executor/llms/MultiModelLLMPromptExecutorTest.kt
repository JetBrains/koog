package ai.koog.prompt.executor.llms

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MultiModelLLMPromptExecutorTest {

    val mockClock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    @Test
    fun testExecuteWithGPT4() = runTest {
        val gpt4Client = MockOpenAILLMClient(executeResponseContent = "GPT-4 response", clock = mockClock)
        val gpt5Client = MockOpenAILLMClient(executeResponseContent = "GPT-5 response", clock = mockClock)

        val executor = MultiModelLLMPromptExecutor(
            llmClients = mapOf(
                OpenAIModels.Chat.GPT4o to gpt4Client,
                OpenAIModels.Chat.GPT5 to gpt5Client
            )
        )

        val model = OpenAIModels.Chat.GPT4o
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model, tools = emptyList()).single()

        assertEquals("GPT-4 response", response.content)
    }

    @Test
    fun testExecuteWithGPT5() = runTest {
        val gpt4Client = MockOpenAILLMClient(executeResponseContent = "GPT-4 response", clock = mockClock)
        val gpt5Client = MockOpenAILLMClient(executeResponseContent = "GPT-5 response", clock = mockClock)

        val executor = MultiModelLLMPromptExecutor(
            llmClients = mapOf(
                OpenAIModels.Chat.GPT4o to gpt4Client,
                OpenAIModels.Chat.GPT5 to gpt5Client
            )
        )

        val model = OpenAIModels.Chat.GPT5
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model, tools = emptyList()).single()

        assertEquals("GPT-5 response", response.content)
    }

    @Test
    fun testExecuteWithFallback() = runTest {
        val gpt4Client = MockOpenAILLMClient(executeResponseContent = "GPT-4 response", clock = mockClock)
        val gpt5Client = MockOpenAILLMClient(executeResponseContent = "GPT-5 fallback response", clock = mockClock)

        val executor = MultiModelLLMPromptExecutor(
            llmClients = mapOf(
                OpenAIModels.Chat.GPT4o to gpt4Client,
                OpenAIModels.Chat.GPT5 to gpt5Client
            ),
            fallback = MultiModelLLMPromptExecutor.FallbackPromptMultiModelExecutorSettings(
                fallbackModel = OpenAIModels.Chat.GPT5,
                fallbackClient = gpt5Client
            )
        )

        val notSupportedModel = OpenAIModels.Chat.GPT4oMini
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = notSupportedModel, tools = emptyList()).single()

        assertEquals("GPT-5 fallback response", response.content)
    }

    @Test
    fun testExecuteStreamingWithGPT4() = runTest {
        val gpt4Client = MockOpenAILLMClient(clock = mockClock)
        val gpt5Client = MockOpenAILLMClient(clock = mockClock)

        val executor = MultiModelLLMPromptExecutor(
            llmClients = mapOf(
                OpenAIModels.Chat.GPT4o to gpt4Client,
                OpenAIModels.Chat.GPT5 to gpt5Client
            )
        )

        val model = OpenAIModels.Chat.GPT4o
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, model, emptyList())
            .filterTextOnly()
            .toList()
        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals(
            "OpenAI streaming response",
            responseChunks.joinToString(""),
            "Response should be from GPT-4 client"
        )
    }

    @Test
    fun testExecuteStreamingWithFallback() = runTest {
        val gpt4Client = MockOpenAILLMClient(clock = mockClock)
        val gpt5Client = MockOpenAILLMClient(clock = mockClock)

        val executor = MultiModelLLMPromptExecutor(
            llmClients = mapOf(
                OpenAIModels.Chat.GPT4o to gpt4Client,
                OpenAIModels.Chat.GPT5 to gpt5Client
            ),
            fallback = MultiModelLLMPromptExecutor.FallbackPromptMultiModelExecutorSettings(
                fallbackModel = OpenAIModels.Chat.GPT5,
                fallbackClient = gpt5Client
            )
        )

        val notSupportedModel = OpenAIModels.Chat.GPT4oMini
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, notSupportedModel, emptyList())
            .filterTextOnly()
            .toList()
        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals(
            "OpenAI streaming response",
            responseChunks.joinToString(""),
            "Response should be from fallback client"
        )
    }

    @Test
    fun testExecuteWithUnsupportedModelNoFallback() = runTest {
        val gpt4Client = MockOpenAILLMClient(clock = mockClock)

        val executor = MultiModelLLMPromptExecutor(
            llmClients = mapOf(
                OpenAIModels.Chat.GPT4o to gpt4Client
            )
        )

        val notSupportedModel = OpenAIModels.Chat.GPT4oMini
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        assertFailsWith<IllegalArgumentException>("Should throw IllegalArgumentException for unsupported model without fallback") {
            executor.execute(prompt = prompt, model = notSupportedModel, tools = emptyList())
        }
    }

    @Test
    fun testExecuteStreamingWithUnsupportedModelNoFallback() = runTest {
        val gpt4Client = MockOpenAILLMClient(clock = mockClock)

        val executor = MultiModelLLMPromptExecutor(
            llmClients = mapOf(
                OpenAIModels.Chat.GPT4o to gpt4Client
            )
        )

        val notSupportedModel = OpenAIModels.Chat.GPT4oMini
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        assertFailsWith<IllegalArgumentException>("Should throw IllegalArgumentException for unsupported model without fallback") {
            executor.executeStreaming(prompt, notSupportedModel, emptyList()).collect()
        }
    }

    @Test
    fun testModelsReturnsMappedModels() = runTest {
        val gpt4Client = MockOpenAILLMClient(clock = mockClock)
        val gpt5Client = MockOpenAILLMClient(clock = mockClock)

        val executor = MultiModelLLMPromptExecutor(
            llmClients = mapOf(
                OpenAIModels.Chat.GPT4o to gpt4Client,
                OpenAIModels.Chat.GPT5 to gpt5Client
            )
        )

        val models = executor.models()

        assertEquals(2, models.size)
        assertEquals(true, models.contains(OpenAIModels.Chat.GPT4o.id))
        assertEquals(true, models.contains(OpenAIModels.Chat.GPT5.id))
    }

    @Test
    fun testFallbackModelMustBeInClients() {
        val gpt4Client = MockOpenAILLMClient(clock = mockClock)
        val gpt5Client = MockOpenAILLMClient(clock = mockClock)
        val fallbackModel = OpenAIModels.Chat.GPT5

        assertFailsWith<IllegalStateException>("Should throw IllegalStateException when fallback model is not in clients") {
            MultiModelLLMPromptExecutor(
                llmClients = mapOf(
                    OpenAIModels.Chat.GPT4o to gpt4Client
                ),
                fallback = MultiModelLLMPromptExecutor.FallbackPromptMultiModelExecutorSettings(
                    fallbackModel = fallbackModel,
                    fallbackClient = gpt5Client
                )
            )
        }
    }
}
