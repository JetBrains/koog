package ai.koog.prompt.executor.clients.openai.base

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMStreamResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/**
 * Tests for token metadata extraction in [AbstractOpenAILLMClient.createMetaInfo].
 *
 * Verifies that cached token counts and reasoning token counts are correctly
 * propagated from [OpenAIUsage] into [ResponseMetaInfo.metadata].
 */
internal class OpenAITokenMetadataTestHelper :
    AbstractOpenAILLMClient<
        OpenAITokenMetadataTestHelper.StubResponse,
        OpenAITokenMetadataTestHelper.StubStreamResponse
    >(
    apiKey = "test-key",
    settings = object : OpenAIBaseSettings(
        baseUrl = "https://test.example.com",
        chatCompletionsPath = "v1/chat/completions"
    ) {},
    logger = KotlinLogging.logger("TokenMetadataTestHelper"),
    toolsConverter = OpenAICompatibleToolDescriptorSchemaGenerator(),
) {

    /**
     * Public wrapper to test [createMetaInfo].
     */
    fun createMetaInfoForTest(usage: OpenAIUsage?): ResponseMetaInfo = createMetaInfo(usage)

    override fun llmProvider(): LLMProvider = LLMProvider.OpenAI

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String = "{}"

    override fun processProviderChatResponse(response: StubResponse): List<LLMChoice> = emptyList()
    override fun decodeStreamingResponse(data: String): StubStreamResponse = StubStreamResponse()
    override fun decodeResponse(data: String): StubResponse = StubResponse()
    override fun processStreamingResponse(response: Flow<StubStreamResponse>): Flow<StreamFrame> = emptyFlow()

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("Not needed in tests")

    @Serializable
    class StubResponse : OpenAIBaseLLMResponse {
        override val id: String = "stub"
        override val model: String = "stub-model"
        override val created: Long = 0L
    }

    @Serializable
    class StubStreamResponse : OpenAIBaseLLMStreamResponse {
        override val id: String = "stub"
        override val model: String = "stub-model"
        override val created: Long = 0L
    }
}
