package ai.koog.prompt.structure

import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.json.JsonStructuredData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StructureFixingParserTest {
    @Serializable
    private data class TestData(
        val a: String,
        val b: Int,
    )

    private val testData = TestData("test", 42)
    private val testDataJson = Json.encodeToString(testData)
    private val testStructure = JsonStructuredData.createJsonStructure<TestData>()

    @Test
    fun testParseValidContentWithoutFixing() = runTest {
        val parser = StructureFixingParser(
            fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
            retries = 2,
        )
        val mockExecutor = getMockExecutor {}

        val result = parser.parse(mockExecutor, testStructure, testDataJson)
        assertEquals(testData, result)
    }

    @Test
    fun testFixInvalidContentInMultipleSteps() = runTest {
        val parser = StructureFixingParser(
            fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
            retries = 2,
        )

        val invalidContent = testDataJson
            .replace("\"a\"\\s*:".toRegex(), "")
            .replace("\"b\"\\s*:".toRegex(), "")

        val firstResponse = testDataJson
            .replace("\"a\"\\s*:".toRegex(), "")

        val mockExecutor = getMockExecutor {
            mockLLMAnswer(firstResponse) onRequestContains invalidContent
            mockLLMAnswer(testDataJson) onRequestContains firstResponse
        }

        val result = parser.parse(mockExecutor, testStructure, invalidContent)
        assertEquals(testData, result)
    }

    @Test
    fun testFailToParseWhenFixingRetriesExceeded() = runTest {
        val parser = StructureFixingParser(
            fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
            retries = 2,
        )

        val invalidContent = testDataJson
            .replace("\"a\"\\s*:".toRegex(), "")
            .replace("\"b\"\\s*:".toRegex(), "")

        val mockExecutor = getMockExecutor {
            mockLLMAnswer(invalidContent).asDefaultResponse
        }

        assertFailsWith<LLMStructuredParsingError> {
            parser.parse(mockExecutor, testStructure, invalidContent)
        }
    }
}
