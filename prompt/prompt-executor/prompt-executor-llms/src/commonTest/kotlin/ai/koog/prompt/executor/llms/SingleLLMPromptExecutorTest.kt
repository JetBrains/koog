package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.testing.client.CapturingLLMClient
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SingleLLMPromptExecutorTest {

    private val mockClock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    private val mockModel: LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = "mock-model",
        capabilities = emptyList(),
        contextLength = 8192,
    )

    val tools = listOf(
        ToolDescriptor("Dummy tool", "Dummy tool description", listOf()),
    )

    @Test
    fun testExecute() = runTest {
        val responses = listOf(
            Message.Assistant("Hello", ResponseMetaInfo.create(mockClock))
        )
        val client = CapturingLLMClient(executeResponses = responses)
        val executor = SingleLLMPromptExecutor(client)

        val prompt = Prompt.build("p1") {
            user("Hello!")
        }

        val result = executor.execute(prompt, mockModel, tools)

        assertEquals(responses, result, "Response should match, got: $result")
        assertEquals(prompt, client.lastExecutedPrompt, "Prompt should match, got: ${client.lastExecutedPrompt}")
        assertEquals(mockModel, client.lastExecutedModel, "Model should match, got: ${client.lastExecutedModel}")
        assertEquals(tools, client.lastExecutedTools, "Tools should match, got: ${client.lastExecutedTools}")
    }

    @Test
    fun testExecuteStreaming() = runTest {
        val chunks = listOf("hello", " ", "world").map(StreamFrame::Append)
        val client = CapturingLLMClient(streamingChunks = chunks)
        val executor = SingleLLMPromptExecutor(client)
        val prompt = Prompt.build("p2") { user("Hello!") }

        val collected = executor.executeStreaming(prompt, mockModel).toList()

        assertEquals(chunks, collected, "Response chunks should match, got: $collected")
        assertEquals(prompt, client.lastStreamingPrompt, "Prompt should match, got: ${client.lastStreamingPrompt}")
        assertEquals(mockModel, client.lastStreamingModel, "Model should match, got: ${client.lastStreamingModel}")
    }

    @Test
    fun testExecuteMultipleChoices() = runTest {
        val meta = ResponseMetaInfo.create(mockClock)
        val choices: List<LLMChoice> = listOf(
            listOf(Message.Assistant("Hi there!", meta)),
            listOf(Message.Assistant("Hello world!", meta)),
        )

        val client = CapturingLLMClient(choices = choices)
        val executor = SingleLLMPromptExecutor(client)
        val prompt = Prompt.build("p3") { user("Hello!") }

        val result = executor.executeMultipleChoices(prompt, mockModel, tools)

        assertEquals(choices, result, "Response should match, got: $result")
        assertEquals(prompt, client.lastChoicesPrompt, "Prompt should match, got: ${client.lastChoicesPrompt}")
        assertEquals(mockModel, client.lastChoicesModel, "Model should match, got: ${client.lastChoicesModel}")
        assertEquals(tools, client.lastChoicesTools, "Tools should match, got: ${client.lastChoicesTools}")
    }

    @Test
    fun testModerate() = runTest {
        val mod = ModerationResult(
            isHarmful = true,
            categories = mapOf(
                ModerationCategory.Harassment to ModerationCategoryResult(detected = true)
            )
        )
        val client = CapturingLLMClient(moderationResult = mod)
        val executor = SingleLLMPromptExecutor(client)
        val prompt = Prompt.build("p4") { user("Hello Huhrensohn") }

        val result = executor.moderate(prompt, mockModel)

        assertSame(mod, result)
        assertSame(prompt, client.lastModerationPrompt)
        assertSame(mockModel, client.lastModerationModel)
    }
}
