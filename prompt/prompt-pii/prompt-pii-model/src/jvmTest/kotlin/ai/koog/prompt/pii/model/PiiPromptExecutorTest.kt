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
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.streamFrameFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiiPromptExecutorTest {
    private val testModel: LLModel = LLModel(
        provider = object : LLMProvider("test", "Test") {},
        id = "test-model",
        capabilities = emptyList(),
        contextLength = 4096L,
    )

    @Test
    fun testAnonymizesPromptReusesSameTagAndDeanonymizesResponse() = runTest {
        val detector = object : PiiDetector {
            override suspend fun detect(text: String): List<PiiDetection> =
                Regex("""John Doe""")
                    .findAll(text)
                    .map {
                        PiiDetection(
                            start = it.range.first,
                            endExclusive = it.range.last + 1,
                            type = PiiType.PERSON
                        )
                    }
                    .toList()
        }

        val capturedPrompts: MutableList<Prompt> = mutableListOf()
        val nested = StubPromptExecutor(
            onExecute = { prompt, _, _ ->
                capturedPrompts += prompt
                listOf(Message.Assistant("Hello [[person 1]]", ResponseMetaInfo.Empty))
            }
        )

        val executor = PiiPromptExecutor(detector = detector, nested = nested)
        val prompt = Prompt(
            messages = listOf(Message.User("John Doe met John Doe", RequestMetaInfo.Empty)),
            id = "test"
        )

        val response = executor.execute(prompt, testModel, emptyList())

        assertEquals(1, response.size)
        assertEquals("Hello John Doe", response.first().content)

        val sentPrompt = capturedPrompts.single()
        assertTrue(sentPrompt.messages.first() is Message.System)
        assertEquals(
            "[[person 1]] met [[person 1]]",
            (sentPrompt.messages[1] as Message.User).content
        )
    }

    @Test
    fun testOverlappingDetectionsPreferLongestSpan() = runTest {
        val detector = object : PiiDetector {
            override suspend fun detect(text: String): List<PiiDetection> =
                listOf(
                    PiiDetection(0, 3, PiiType.PERSON),
                    PiiDetection(0, 6, PiiType.EMAIL_ADDRESS),
                    PiiDetection(2, 4, PiiType.PHONE_NUMBER)
                )
        }

        val capturedPrompts: MutableList<Prompt> = mutableListOf()
        val nested = StubPromptExecutor(
            onExecute = { prompt, _, _ ->
                capturedPrompts += prompt
                listOf(Message.Assistant("ok", ResponseMetaInfo.Empty))
            }
        )

        val executor = PiiPromptExecutor(detector = detector, nested = nested)
        val prompt = Prompt(messages = listOf(Message.User("abcdef", RequestMetaInfo.Empty)), id = "overlap")

        executor.execute(prompt, testModel, emptyList())

        val anonymizedContent = (capturedPrompts.single().messages[1] as Message.User).content
        assertEquals("[[email_address 1]]", anonymizedContent)
    }

    @Test
    fun testNoInstructionIsAddedWhenNoPiiDetected() = runTest {
        val detector = object : PiiDetector {
            override suspend fun detect(text: String): List<PiiDetection> = emptyList()
        }

        val capturedPrompts: MutableList<Prompt> = mutableListOf()
        val nested = StubPromptExecutor(
            onExecute = { prompt, _, _ ->
                capturedPrompts += prompt
                listOf(Message.Assistant("ok", ResponseMetaInfo.Empty))
            }
        )

        val prompt = Prompt(messages = listOf(Message.User("Hello world", RequestMetaInfo.Empty)), id = "plain")
        val executor = PiiPromptExecutor(detector = detector, nested = nested)

        executor.execute(prompt, testModel, emptyList())

        val sentPrompt = capturedPrompts.single()
        assertEquals(1, sentPrompt.messages.size)
        assertFalse(sentPrompt.messages.first() is Message.System)
        assertEquals("Hello world", sentPrompt.messages.first().content)
    }

    @Test
    fun testUnknownTagsFailFastForExecute() = runTest {
        val detector = object : PiiDetector {
            override suspend fun detect(text: String): List<PiiDetection> =
                listOf(PiiDetection(0, text.length, PiiType.PERSON))
        }

        val nested = StubPromptExecutor(
            onExecute = { _, _, _ ->
                listOf(Message.Assistant("Hi [[person 2]]", ResponseMetaInfo.Empty))
            }
        )

        val executor = PiiPromptExecutor(detector = detector, nested = nested)
        val prompt = Prompt(messages = listOf(Message.User("John Doe", RequestMetaInfo.Empty)), id = "unknown")

        val error = assertFailsWith<UnknownPiiTagsException> {
            executor.execute(prompt, testModel, emptyList())
        }

        assertContains(error.unknownTags, "[[person 2]]")
    }

    @Test
    fun testFixerCanRecoverUnknownTagsInNonStreamingExecute() = runTest {
        val detector = object : PiiDetector {
            override suspend fun detect(text: String): List<PiiDetection> =
                listOf(PiiDetection(0, text.length, PiiType.PERSON))
        }

        val nested = StubPromptExecutor(
            onExecute = { prompt, _, _ ->
                if (prompt.id == "pii-tag-fixing") {
                    listOf(Message.Assistant("Hello [[person 1]]", ResponseMetaInfo.Empty))
                } else {
                    listOf(Message.Assistant("Hello [[person 2]]", ResponseMetaInfo.Empty))
                }
            }
        )

        val fixer = PiiTagFixingParser(model = testModel, retries = 1)
        val executor = PiiPromptExecutor(detector = detector, nested = nested, fixingParser = fixer)
        val prompt = Prompt(messages = listOf(Message.User("John Doe", RequestMetaInfo.Empty)), id = "recover")

        val response = executor.execute(prompt, testModel, emptyList())

        assertEquals("Hello John Doe", response.single().content)
    }

    @Test
    fun testStreamingHandlesSplitTagBoundaries() = runTest {
        val detector = object : PiiDetector {
            override suspend fun detect(text: String): List<PiiDetection> =
                listOf(PiiDetection(0, text.length, PiiType.PERSON))
        }

        val nested = StubPromptExecutor(
            onExecute = { _, _, _ -> error("Not used") },
            onExecuteStreaming = { _, _, _ ->
                streamFrameFlow {
                    emit(StreamFrame.TextDelta("Hello [[per"))
                    emit(StreamFrame.TextDelta("son 1]]!"))
                    emit(StreamFrame.End(metaInfo = ResponseMetaInfo.Empty))
                }
            }
        )

        val executor = PiiPromptExecutor(detector = detector, nested = nested)
        val prompt = Prompt(messages = listOf(Message.User("John Doe", RequestMetaInfo.Empty)), id = "stream")

        val frames: List<StreamFrame> = executor.executeStreaming(prompt, testModel, emptyList()).toList()

        val text = frames
            .filterIsInstance<StreamFrame.TextDelta>()
            .joinToString(separator = "") { it.text }

        assertEquals("Hello John Doe!", text)
    }

    @Test
    fun testStreamingFailsFastOnUnknownCompleteTagEvenWhenFixerExists() = runTest {
        val detector = object : PiiDetector {
            override suspend fun detect(text: String): List<PiiDetection> =
                listOf(PiiDetection(0, text.length, PiiType.PERSON))
        }

        val nested = StubPromptExecutor(
            onExecute = { _, _, _ ->
                listOf(Message.Assistant("unused", ResponseMetaInfo.Empty))
            },
            onExecuteStreaming = { _, _, _ ->
                streamFrameFlow {
                    emit(StreamFrame.TextDelta("Hi [[person 2]]"))
                    emit(StreamFrame.End(metaInfo = ResponseMetaInfo.Empty))
                }
            }
        )

        val fixer = PiiTagFixingParser(model = testModel, retries = 2)
        val executor = PiiPromptExecutor(detector = detector, nested = nested, fixingParser = fixer)
        val prompt = Prompt(messages = listOf(Message.User("John Doe", RequestMetaInfo.Empty)), id = "stream-fail")

        assertFailsWith<UnknownPiiTagsException> {
            executor.executeStreaming(prompt, testModel, emptyList()).toList()
        }
    }

    private class StubPromptExecutor(
        private val onExecute: suspend (Prompt, LLModel, List<ToolDescriptor>) -> List<Message.Response>,
        private val onExecuteStreaming: (Prompt, LLModel, List<ToolDescriptor>) -> Flow<StreamFrame> =
            { _, _, _ -> streamFrameFlow { emit(StreamFrame.End()) } },
    ) : PromptExecutor() {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> = onExecute(prompt, model, tools)

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> = onExecuteStreaming(prompt, model, tools)

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            ModerationResult(
                isHarmful = false,
                categories = mapOf(ModerationCategory.Harassment to ModerationCategoryResult(detected = false))
            )

        override fun close() {}
    }
}
