package ai.koog.prompt.executor.clients.bedrock.modelfamilies.cohere

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BedrockCohereSerializationTest {

    @Test
    fun `createV3TextRequest with minimal params generates valid JSON`() {
        val json = BedrockCohereSerialization.createV3TextRequest(
            texts = listOf("hello world")
        )
        assertTrue(json.contains("hello world"))
        assertTrue(json.contains("texts"))
        assertTrue(json.contains("{"))
    }

    @Test
    fun `createV3TextRequest with all optional params generates valid JSON`() {
        val json = BedrockCohereSerialization.createV3TextRequest(
            texts = listOf("t1", "t2"),
            inputType = "search_query",
            truncate = "END",
            embeddingTypes = listOf("float", "int8")
        )
        assertTrue(json.contains("search_query"))
        assertTrue(json.contains("END"))
        assertTrue(json.contains("float"))
        assertTrue(json.contains("int8"))
        assertTrue(json.contains("t1"))
        assertTrue(json.contains("t2"))
    }

    @Test
    fun `parseResponse returns correct CohereEmbedResponse object for multi-text float batch`() {
        val responseJson = """
            {
                "id": "rsp123",
                "response_type": "embeddings",
                "embeddings": {
                    "float": [
                        [0.1, 0.2],
                        [0.3, 0.4]
                    ],
                    "int8": [
                        [1, 2],
                        [3, 4]
                    ]
                },
                "texts": ["foo", "bar"]
            }
        """.trimIndent()

        val resp = BedrockCohereSerialization.parseResponse(responseJson)
        assertEquals("rsp123", resp.id)
        assertEquals("embeddings", resp.responseType)
        assertNotNull(resp.embeddings)
        assertTrue(resp.embeddings.containsKey("float"))
        assertEquals(listOf(listOf(0.1, 0.2), listOf(0.3, 0.4)), resp.embeddings["float"])
        assertEquals(listOf("foo", "bar"), resp.texts)
    }

    @Test
    fun `extractEmbeddings returns all float embeddings per input`() {
        val responseJson = """
            {
                "embeddings": {
                    "float": [
                        [0.5, 0.6, 0.7],
                        [1.1, 1.2, 1.3]
                    ]
                },
                "texts": ["input1", "input2"]
            }
        """.trimIndent()

        val resp = BedrockCohereSerialization.parseResponse(responseJson)
        val all = BedrockCohereSerialization.extractEmbeddings(resp, type = "float")
        assertEquals(2, all.size)
        assertEquals(listOf(0.5, 0.6, 0.7), all.first())
        assertEquals(listOf(1.1, 1.2, 1.3), all[1])
    }

    @Test
    fun `extractFirstFloatEmbedding returns floats for single text input`() {
        val responseJson = """
            {
                "embeddings": {
                    "float": [
                        [2.5, 3.1]
                    ]
                }
            }
        """.trimIndent()

        val resp = BedrockCohereSerialization.parseResponse(responseJson)
        val first = BedrockCohereSerialization.extractFirstFloatEmbedding(resp)
        assertEquals(listOf(2.5, 3.1), first)
    }

    @Test
    fun `extractEmbeddings throws if requested type is missing`() {
        val responseJson = """
            {
                "embeddings": {
                    "float": [
                        [0.0, 0.1]
                    ]
                }
            }
        """.trimIndent()
        val resp = BedrockCohereSerialization.parseResponse(responseJson)
        assertFailsWith<IllegalStateException> {
            BedrockCohereSerialization.extractEmbeddings(resp, type = "binary")
        }
    }

    @Test
    fun `extractFirstFloatEmbedding throws if no embeddings present`() {
        val responseJson = """
            {
                "embeddings": {
                    "float": []
                }
            }
        """.trimIndent()
        val resp = BedrockCohereSerialization.parseResponse(responseJson)
        assertFailsWith<IllegalStateException> {
            BedrockCohereSerialization.extractFirstFloatEmbedding(resp)
        }
    }

    @Test
    fun `parseResponse handles only int8 embeddings`() {
        val responseJson = """
            {
                "embeddings": {
                    "int8": [
                        [1, -1, 1],
                        [0, 2, -2]
                    ]
                }
            }
        """.trimIndent()
        val resp = BedrockCohereSerialization.parseResponse(responseJson)
        val ints = BedrockCohereSerialization.extractEmbeddings(resp, type = "int8")
        assertEquals(listOf(1, -1, 1), ints[0].map { it.toInt() })
        assertEquals(listOf(0, 2, -2), ints[1].map { it.toInt() })
    }
}
