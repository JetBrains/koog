package ai.koog.prompt.pii.structured

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
import ai.koog.prompt.pii.model.PiiType
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.streamFrameFlow
import ai.koog.prompt.structure.LLMStructuredParsingError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructuredPiiDetectorTest {
    private val testModel: LLModel = LLModel(
        provider = object : LLMProvider("test", "Test") {},
        id = "test-model",
        capabilities = emptyList(),
        contextLength = 4096L,
    )

    @Test
    fun testStructuredHappyPath() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    assistantJson(
                        """{"detections":[{"substring":"John Doe","type":"PERSON"},{"substring":"john@example.com","type":"EMAIL_ADDRESS"}]}"""
                    )
                )
            )
        )
        val detector = StructuredPiiDetector(executor = executor, model = testModel)
        val text = "Contact John Doe at john@example.com"

        val detections = detector.detect(text)

        assertEquals(2, detections.size)
        assertEquals(PiiType.PERSON, detections[0].type)
        assertEquals(text.indexOf("John Doe"), detections[0].start)
        assertEquals(text.indexOf("John Doe") + "John Doe".length, detections[0].endExclusive)
        assertEquals(PiiType.EMAIL_ADDRESS, detections[1].type)
    }

    @Test
    fun testAllOccurrenceExpansion() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    assistantJson("""{"detections":[{"substring":"John","type":"PERSON"}]}""")
                )
            )
        )
        val detector = StructuredPiiDetector(executor = executor, model = testModel)

        val detections = detector.detect("John met John")

        assertEquals(2, detections.size)
        assertEquals(0, detections[0].start)
        assertEquals(9, detections[1].start)
    }

    @Test
    fun testDeduplicatesSameSpanAndType() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    assistantJson(
                        """{"detections":[{"substring":"John","type":"PERSON"},{"substring":"John","type":"PERSON"}]}"""
                    )
                )
            )
        )
        val detector = StructuredPiiDetector(executor = executor, model = testModel)

        val detections = detector.detect("John")

        assertEquals(1, detections.size)
        assertEquals(PiiType.PERSON, detections.single().type)
    }

    @Test
    fun testExactLiteralCaseSensitivity() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    assistantJson("""{"detections":[{"substring":"john","type":"PERSON"}]}""")
                )
            )
        )
        val detector = StructuredPiiDetector(executor = executor, model = testModel)

        val detections = detector.detect("John")

        assertTrue(detections.isEmpty())
    }

    @Test
    fun testUnmatchedSubstringIsIgnored() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    assistantJson("""{"detections":[{"substring":"Jane","type":"PERSON"}]}""")
                )
            )
        )
        val detector = StructuredPiiDetector(executor = executor, model = testModel)

        val detections = detector.detect("John")

        assertTrue(detections.isEmpty())
    }

    @Test
    fun testOverlappingOccurrenceBehavior() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    assistantJson("""{"detections":[{"substring":"ana","type":"LOCATION"}]}""")
                )
            )
        )
        val detector = StructuredPiiDetector(executor = executor, model = testModel)

        val detections = detector.detect("banana")

        assertEquals(2, detections.size)
        assertEquals(1, detections[0].start)
        assertEquals(3, detections[1].start)
    }

    @Test
    fun testFixingParserRecovery() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    Message.Assistant("not valid json", ResponseMetaInfo.Empty)
                ),
                "structure-fixing" to listOf(
                    assistantJson("""{"detections":[{"substring":"John","type":"PERSON"}]}""")
                )
            )
        )
        val detector = StructuredPiiDetector(
            executor = executor,
            model = testModel,
            config = StructuredPiiDetectorConfig(fixingRetries = 1),
        )

        val detections = detector.detect("John")

        assertEquals(1, detections.size)
        assertEquals(PiiType.PERSON, detections.single().type)
    }

    @Test
    fun testFixingParserExhaustedThrows() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    Message.Assistant("not valid json", ResponseMetaInfo.Empty)
                ),
                "structure-fixing" to listOf(
                    Message.Assistant("still not valid json", ResponseMetaInfo.Empty)
                )
            )
        )
        val detector = StructuredPiiDetector(
            executor = executor,
            model = testModel,
            config = StructuredPiiDetectorConfig(fixingRetries = 1),
        )

        assertFailsWith<LLMStructuredParsingError> {
            detector.detect("John")
        }
    }

    @Test
    fun testEnumSerializationSmoke() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    assistantJson("""{"detections":[{"substring":"123-45-6789","type":"US_SSN"}]}""")
                )
            )
        )
        val detector = StructuredPiiDetector(executor = executor, model = testModel)
        val text = "SSN: 123-45-6789"

        val detections = detector.detect(text)

        assertEquals(1, detections.size)
        assertEquals(PiiType.US_SSN, detections.single().type)
        assertEquals(text.indexOf("123-45-6789"), detections.single().start)
    }

    @Test
    fun testCustomPromptIdIsUsed() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "custom-id" to listOf(
                    assistantJson("""{"detections":[{"substring":"John","type":"PERSON"}]}""")
                )
            )
        )
        val detector = StructuredPiiDetector(
            executor = executor,
            model = testModel,
            config = StructuredPiiDetectorConfig(promptId = "custom-id"),
        )

        val detections = detector.detect("John")

        assertEquals(1, detections.size)
        assertEquals("custom-id", executor.executedPromptIds.single())
        assertFalse(executor.executedPromptIds.contains("pii-structured-detection"))
    }

    @Test
    fun testCustomPromptTemplateIsApplied() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    assistantJson("""{"detections":[{"substring":"John","type":"PERSON"}]}""")
                )
            )
        )
        val detector = StructuredPiiDetector(
            executor = executor,
            model = testModel,
            config = StructuredPiiDetectorConfig(
                promptTemplate = { builder, text ->
                    builder.apply {
                        system("CUSTOM_PROMPT_MARKER")
                        user("TEXT:$text")
                    }
                }
            ),
        )

        detector.detect("John")

        val sentPrompt = executor.executedPrompts.single()
        assertTrue(sentPrompt.messages.any { it.content.contains("CUSTOM_PROMPT_MARKER") })
        assertTrue(sentPrompt.messages.any { it.content.contains("TEXT:John") })
    }

    @Test
    fun testFixingRetriesZeroDoesNotCallStructureFixing() = runTest {
        val executor = ScriptedPromptExecutor(
            responsesByPromptId = mapOf(
                "pii-structured-detection" to listOf(
                    Message.Assistant("not valid json", ResponseMetaInfo.Empty)
                ),
                "structure-fixing" to listOf(
                    assistantJson("""{"detections":[{"substring":"John","type":"PERSON"}]}""")
                )
            )
        )
        val detector = StructuredPiiDetector(
            executor = executor,
            model = testModel,
            config = StructuredPiiDetectorConfig(fixingRetries = 0),
        )

        assertFailsWith<LLMStructuredParsingError> {
            detector.detect("John")
        }

        assertFalse(executor.executedPromptIds.contains("structure-fixing"))
        assertEquals(listOf("pii-structured-detection"), executor.executedPromptIds)
    }

    private class ScriptedPromptExecutor(
        responsesByPromptId: Map<String, List<Message.Response>>,
    ) : PromptExecutor() {
        val executedPromptIds: MutableList<String> = mutableListOf()
        val executedPrompts: MutableList<Prompt> = mutableListOf()

        private val queues: MutableMap<String, ArrayDeque<Message.Response>> =
            responsesByPromptId.mapValues { (_, values) -> ArrayDeque(values) }.toMutableMap()

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> {
            executedPromptIds += prompt.id
            executedPrompts += prompt

            val queue: ArrayDeque<Message.Response> = queues[prompt.id]
                ?: error("No scripted responses configured for prompt id '${prompt.id}'")
            val response: Message.Response = queue.removeFirstOrNull()
                ?: error("No scripted response left for prompt id '${prompt.id}'")
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

    private fun assistantJson(content: String): Message.Assistant =
        Message.Assistant(content, ResponseMetaInfo.Empty)
}
