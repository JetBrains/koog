package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LLMBasedToolJsonFixTest {
    private companion object {
        private val testClock: Clock = object : Clock {
            override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
        }

        private val testMetaInfo = ResponseMetaInfo.create(testClock)

        private val notIntendedToolCall = Message.Assistant(Prompts.NOT_INTENDED_TOOL_CALL, metaInfo = testMetaInfo)
        private val intendedToolCall = Message.Assistant(Prompts.INTENDED_TOOL_CALL, metaInfo = testMetaInfo)
        private val toolCallMessage = Message.Tool.Call(
            id = null,
            tool = "plus",
            content = """{"a":5,"b":3}""",
            metaInfo = testMetaInfo
        )

        private val toolRegistry = Tools.toolRegistry

        private val tools = toolRegistry.tools.map { it.descriptor }
        private val prompt = prompt("test-prompt") { }
        private val model = OpenAIModels.Chat.GPT4o

        private val message = Message.Assistant("I want to use the calculator tool", metaInfo = testMetaInfo)

        val processor = LLMBasedToolCallFixProcessor(toolRegistry)
    }

    private class MockExecutor(
        private val responses: List<Message.Response>,
    ) : PromptExecutor {
        private var index = 0
        val prompts = mutableListOf<Prompt>()

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> =
            listOf(responses[index++]).also { prompts.add(prompt) }

        override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
            error("Not supported")

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("Not supported")
        override fun close() {}
    }

    private suspend fun process(
        executor: PromptExecutor,
        response: Message.Response,
        processor: ResponseProcessor
    ) = processor.process(executor, prompt, model, tools, response)

    @Test
    fun test_shouldStopIfToolCallNotIntended() = runTest {
        val executor = MockExecutor(listOf(notIntendedToolCall))
        val result = process(executor, message, processor)

        assertEquals(message, result)
        assertEquals(
            Prompts.assessToolCallIntent,
            executor.prompts.last().messages.dropLast(1).last().content
        )
    }

    @Test
    fun test_shouldFixAssistantMessage() = runTest {
        val executor = MockExecutor(listOf(intendedToolCall, toolCallMessage))
        val result = process(executor, message, processor)

        assertEquals(toolCallMessage, result)
        assertEquals(
            Prompts.fixToolCall,
            executor.prompts.last().messages.dropLast(2).last().content
        )
        assertEquals(
            Prompts.invalidJsonFeedback(tools),
            executor.prompts.last().messages.last().content
        )
    }

    @Test
    fun test_shouldFixInvalidToolName() = runTest {
        val executor = MockExecutor(listOf(toolCallMessage))
        val message = toolCallMessage.copy(tool = "minus")
        val result = process(executor, message, processor)

        assertEquals(toolCallMessage, result)
        assertEquals(
            Prompts.invalidNameFeedback("minus", tools),
            executor.prompts.last().messages.last().content
        )
    }

    @Test
    fun test_shouldFixIncorrectArguments() = runTest {
        val executor = MockExecutor(listOf(toolCallMessage))
        val message = Message.Tool.Call(
            id = null,
            tool = "plus",
            content = """{"x":5,"y":3}""",
            metaInfo = testMetaInfo
        )
        val result = process(executor, message, processor)

        assertEquals(toolCallMessage, result)
        assertContains(
            executor.prompts.last().messages.last().content,
            "Failed to parse tool arguments with error"
        )
    }

    @Test
    fun test_shouldRetry() = runTest {
        val executor = MockExecutor(listOf(intendedToolCall, toolCallMessage.copy(tool = "minus"), toolCallMessage))
        val result = process(executor, message, processor)

        assertEquals(toolCallMessage, result)
    }

    @Test
    fun test_shouldStopWhenMaxRetriesReached() = runTest {
        val executor = MockExecutor(listOf(intendedToolCall, toolCallMessage.copy(tool = "minus")))
        val processor = LLMBasedToolCallFixProcessor(
            toolRegistry = toolRegistry,
            maxRetries = 1,
        )
        val result = process(executor, message, processor)

        assertEquals(message, result)
    }

    @Test
    fun test_shouldApplyFallbackWhenMaxRetriesReached() = runTest {
        val executor = MockExecutor(listOf(intendedToolCall, toolCallMessage))
        val fallbackExecutor = MockExecutor(listOf(toolCallMessage))

        val fallbackProcessor = object : ResponseProcessor() {
            override suspend fun process(
                executor: PromptExecutor,
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>,
                responses: List<Message.Response>
            ): List<Message.Response> = fallbackExecutor.execute(prompt, model, tools)
        }

        val processor = LLMBasedToolCallFixProcessor(
            toolRegistry = toolRegistry,
            fallbackProcessor = fallbackProcessor,
            maxRetries = 1,
        )

        val result = process(executor, message, processor)

        assertEquals(toolCallMessage, result)
    }
}
