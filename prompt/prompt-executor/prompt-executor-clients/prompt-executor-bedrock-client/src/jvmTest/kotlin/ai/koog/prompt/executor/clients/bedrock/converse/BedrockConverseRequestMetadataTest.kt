package ai.koog.prompt.executor.clients.bedrock.converse

import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BedrockConverseRequestMetadataTest {

    private val model = BedrockModels.AnthropicClaude4Sonnet
    private val metadata = mapOf("turnId" to "turn-1", "conversationId" to "conversation-1")

    private fun promptWith(params: BedrockConverseParams) = Prompt.build("test", params) { user("Hello") }

    @Test
    fun testConverseRequestWithRequestMetadata() {
        val request = BedrockConverseConverters.createConverseRequest(
            promptWith(BedrockConverseParams(requestMetadata = metadata)),
            model,
            emptyList(),
        )
        assertEquals(metadata, request.requestMetadata)
    }

    @Test
    fun testConverseStreamRequestWithRequestMetadata() {
        val request = BedrockConverseConverters.createConverseStreamRequest(
            promptWith(BedrockConverseParams(requestMetadata = metadata)),
            model,
            emptyList(),
        )
        assertEquals(metadata, request.requestMetadata)
    }

    @Test
    fun testConverseStreamRequestWithoutRequestMetadata() {
        val request = BedrockConverseConverters.createConverseStreamRequest(
            promptWith(BedrockConverseParams()),
            model,
            emptyList(),
        )
        assertNull(request.requestMetadata)
    }
}
