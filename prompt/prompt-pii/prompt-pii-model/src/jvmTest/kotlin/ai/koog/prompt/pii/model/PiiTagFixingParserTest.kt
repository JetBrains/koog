package ai.koog.prompt.pii.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.streamFrameFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PiiTagFixingParserTest {
    private val testModel: LLModel = LLModel(
        provider = object : LLMProvider("test", "Test") {},
        id = "test-model",
        capabilities = emptyList(),
        contextLength = 4096L
    )

    @Test
    fun testReturnsContentAsIsWhenNoUnknownTags() = runTest {
        val parser = PiiTagFixingParser(model = testModel, retries = 1)
        val executor = FixedResponseExecutor(listOf(Message.Assistant("unused", ResponseMetaInfo.Empty)))

        val result = parser.fix(
            executor = executor,
            content = "Hello [[person 1]]",
            knownTags = setOf("[[person 1]]")
        )

        assertEquals("Hello [[person 1]]", result)
    }

    @Test
    fun testRetriesAndSucceeds() = runTest {
        val parser = PiiTagFixingParser(model = testModel, retries = 2)
        val executor = FixedResponseExecutor(
            listOf(
                Message.Assistant("Hello [[person 2]]", ResponseMetaInfo.Empty),
                Message.Assistant("Hello [[person 1]]", ResponseMetaInfo.Empty)
            )
        )

        val result = parser.fix(
            executor = executor,
            content = "Hello [[person 2]]",
            knownTags = setOf("[[person 1]]")
        )

        assertEquals("Hello [[person 1]]", result)
    }

    @Test
    fun testThrowsWhenRetriesExceeded() = runTest {
        val parser = PiiTagFixingParser(model = testModel, retries = 1)
        val executor = FixedResponseExecutor(
            listOf(Message.Assistant("Still [[person 9]]", ResponseMetaInfo.Empty))
        )

        assertFailsWith<UnknownPiiTagsException> {
            parser.fix(
                executor = executor,
                content = "Hello [[person 9]]",
                knownTags = setOf("[[person 1]]")
            )
        }
    }

    private class FixedResponseExecutor(
        private val responses: List<Message.Response>,
    ) : PromptExecutor {
        private var idx: Int = 0

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> {
            val response = responses[idx.coerceAtMost(responses.lastIndex)]
            idx += 1
            return listOf(response)
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> = streamFrameFlow {
            emit(StreamFrame.End())
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            ModerationResult(
                isHarmful = false,
                categories = mapOf(ModerationCategory.Harassment to ModerationCategoryResult(detected = false))
            )

        override fun close() {}
    }
}

