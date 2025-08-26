package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    private class CapturingLLMClient(
        private val executeResponses: List<Message.Response> = emptyList(),
        private val streamingChunks: List<String> = emptyList(),
        private val choices: List<LLMChoice> = emptyList(),
        private val moderationResult: ModerationResult = ModerationResult(isHarmful = false, categories = emptyMap()),
    ) : LLMClient {
        var lastExecutedPrompt: Prompt? = null
        var lastExecutedModel: LLModel? = null
        var lastExecutedTools: List<ToolDescriptor>? = null

        var lastStreamingPrompt: Prompt? = null
        var lastStreamingModel: LLModel? = null

        var lastChoicesPrompt: Prompt? = null
        var lastChoicesModel: LLModel? = null
        var lastChoicesTools: List<ToolDescriptor>? = null

        var lastModerationPrompt: Prompt? = null
        var lastModerationModel: LLModel? = null

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            lastExecutedPrompt = prompt
            lastExecutedModel = model
            lastExecutedTools = tools
            return executeResponses
        }

        override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            lastStreamingPrompt = prompt
            lastStreamingModel = model
            return flowOf(*streamingChunks.toTypedArray())
        }

        override suspend fun executeMultipleChoices(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<LLMChoice> {
            lastChoicesPrompt = prompt
            lastChoicesModel = model
            lastChoicesTools = tools
            return choices
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            lastModerationPrompt = prompt
            lastModerationModel = model
            return moderationResult
        }
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
        val chunks = listOf("hello", " ", "world")
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
