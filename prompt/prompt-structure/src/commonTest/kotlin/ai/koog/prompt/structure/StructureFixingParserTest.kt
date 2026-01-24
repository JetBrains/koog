package ai.koog.prompt.structure

import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.json.JsonStructure
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StructureFixingParserTest {
    @Serializable
    private data class TestData(
        val a: String,
        val b: Int,
    )

    @Serializable
    private data class DataWithWildcard(
        val id: String,
        val payload: JsonElement
    )

    private val testData = TestData("test", 42)
    private val testDataJson = Json.encodeToString(testData)
    private val testStructure = JsonStructure.create<TestData>()

    @Test
    fun testParseValidContentWithoutFixing() = runTest {
        val parser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4oMini,
            retries = 2,
        )
        val mockExecutor = getMockExecutor {}

        val result = parser.parse(mockExecutor, testStructure, testDataJson)
        assertEquals(testData, result)
    }

    @Test
    fun testFixInvalidContentInMultipleSteps() = runTest {
        val parser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4oMini,
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
            model = OpenAIModels.Chat.GPT4oMini,
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

    @Test
    fun testFixInvalidJsonElementContent() = runTest {
        val parser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4oMini,
            retries = 2,
        )

        val structure = JsonStructure.create<DataWithWildcard>()

        val invalidContent = """
            {
                "id": "test-id",
                "payload": { 
                    unquotedKey: "someValue",
                    brokenArray: [1, 2 
                }
            }
        """.trimIndent()

        val fixedContent = """
            {
                "id": "test-id",
                "payload": { 
                    "unquotedKey": "someValue",
                    "brokenArray": [1, 2] 
                }
            }
        """.trimIndent()

        val mockExecutor = getMockExecutor {
            mockLLMAnswer(fixedContent) onRequestContains "unquotedKey"
        }

        val result = parser.parse(mockExecutor, structure, invalidContent)

        assertEquals("test-id", result.id)
        assertTrue(result.payload is JsonObject)

        val payloadObj = result.payload
        assertEquals(JsonPrimitive("someValue"), payloadObj["unquotedKey"])
    }
}
