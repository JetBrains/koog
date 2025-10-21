package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BedrockAmazonTitanEmbeddingSerializationTest {

    @Test
    fun `createG1Request generates correct JSON`() {
        val text = "test input"
        val json = BedrockAmazonTitanEmbeddingSerialization.createG1Request(text)
        assertTrue(json.contains("inputText"))
        assertTrue(json.contains(text))
    }

    @Test
    fun `createV2Request generates correct JSON with only required param`() {
        val text = "abc"
        val json = BedrockAmazonTitanEmbeddingSerialization.createV2Request(text)
        assertTrue(json.contains("inputText"))
        assertTrue(json.contains(text))
        // Should NOT contain any optional fields by default
        assertTrue(!json.contains("dimensions"))
        assertTrue(!json.contains("normalize"))
        assertTrue(!json.contains("embeddingTypes"))
    }

    @Test
    fun `createV2Request generates correct JSON with optional params`() {
        val json = BedrockAmazonTitanEmbeddingSerialization.createV2Request(
            text = "def",
            dimensions = 128,
            normalize = true,
            embeddingTypes = listOf("float", "binary")
        )
        assertTrue(json.contains("128"))
        assertTrue(json.contains("true"))
        assertTrue(json.contains("float"))
        assertTrue(json.contains("binary"))
    }

    @Test
    fun `parseG1Response returns correct TitanEmbedTextG1Response`() {
        val responseJson = """
            {
                "embedding": [0.33, 0.44, -0.5],
                "inputTextTokenCount": 23
            }
        """.trimIndent()
        val resp = BedrockAmazonTitanEmbeddingSerialization.parseG1Response(responseJson)
        assertEquals(listOf(0.33, 0.44, -0.5), resp.embedding)
        assertEquals(23, resp.inputTextTokenCount)
    }

    @Test
    fun `parseV2Response returns correct embedding directly`() {
        val responseJson = """
            {
                "embedding": [1.2, -2.3, 0.5],
                "inputTextTokenCount": 10
            }
        """.trimIndent()
        val resp = BedrockAmazonTitanEmbeddingSerialization.parseV2Response(responseJson)
        assertEquals(listOf(1.2, -2.3, 0.5), resp.embedding)
        assertEquals(10, resp.inputTextTokenCount)
    }

    @Test
    fun `parseV2Response returns correct embeddingsByType as fallback`() {
        val responseJson = """
            {
                "embedding": null,
                "inputTextTokenCount": 15,
                "embeddingsByType": {
                    "float": [0.1, 0.2, 0.3],
                    "binary": [1,0,1]
                }
            }
        """.trimIndent()
        val resp = BedrockAmazonTitanEmbeddingSerialization.parseV2Response(responseJson)
        val value = BedrockAmazonTitanEmbeddingSerialization.extractV2Embedding(resp)
        assertEquals(listOf(0.1, 0.2, 0.3), value)
    }

    @Test
    fun `extractV2Embedding throws when no float in response`() {
        val responseJson = """
            {
                "embedding": null,
                "inputTextTokenCount": 20,
                "embeddingsByType": {
                    "binary": [1,1,0]
                }
            }
        """.trimIndent()
        val resp = BedrockAmazonTitanEmbeddingSerialization.parseV2Response(responseJson)
        assertFailsWith<IllegalStateException> {
            BedrockAmazonTitanEmbeddingSerialization.extractV2Embedding(resp)
        }
    }
}
