package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MockLLMClient.ResponseSpec.Companion.executeStreamingSuccess
import ai.koog.prompt.executor.llms.MockLLMClient.ResponseSpec.Companion.executeSuccess
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

internal class MockLLMClient @JvmOverloads constructor(
    private val provider: LLMProvider = LLMProvider.OpenAI,
    val responseSpec: ResponseSpec = ResponseSpec.defaultSpec(provider),
    val models: List<LLModel> = emptyList(),
) : LLMClient {

    internal data class ResponseSpec(
        val execute: Result<List<Message.Assistant>>,
        val executeStreaming: Result<Flow<StreamFrame>>,
        val executeMultipleChoices: Result<List<LLMChoice>>,
        val moderate: Result<ModerationResult>
    ) {

        companion object {
            fun defaultSpec(provider: LLMProvider) = ResponseSpec(
                execute = executeSuccess("${provider.display} response"),
                executeStreaming = executeStreamingSuccess(provider.display, " streaming", " response"),
                executeMultipleChoices = Result.success(emptyList()),
                moderate = Result.success(ModerationResult(false, emptyMap()))
            )

            val failingSpec = ResponseSpec(
                execute = Result.failure(IllegalStateException("Mock failed to execute")),
                executeStreaming = Result.failure(IllegalStateException("Mock failed to execute streaming")),
                executeMultipleChoices =
                Result.failure(IllegalStateException("Mock failed to execute multiple choices")),
                moderate = Result.failure(IllegalStateException("Mock failed to moderate"))
            )

            fun executeSuccess(vararg messages: String) =
                Result.success(messages.map { Message.Assistant(it, ResponseMetaInfo.Empty) }.toList())

            fun executeStreamingSuccess(vararg messages: String) =
                Result.success(flowOf(*messages).map(StreamFrame::TextDelta))
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun simpleClientMock(
            provider: LLMProvider,
            executionResponse: String
        ) = MockLLMClient(
            provider = provider,
            responseSpec = ResponseSpec.defaultSpec(provider).copy(execute = executeSuccess(executionResponse))
        )

        @JvmStatic
        fun failingClientMock(provider: LLMProvider) = MockLLMClient(
            provider = provider,
            responseSpec = ResponseSpec.failingSpec
        )

        operator fun invoke(
            provider: LLMProvider = LLMProvider.OpenAI,
            executeSpec: Result<List<Message.Assistant>>? = null,
            executeStreamingSpec: Result<Flow<StreamFrame>>? = null,
            executeMultipleChoicesSpec: Result<List<LLMChoice>>? = null,
            moderateSpec: Result<ModerationResult>? = null,
        ): MockLLMClient {
            val defaultSpec = ResponseSpec.defaultSpec(provider)
            return MockLLMClient(
                provider = provider,
                responseSpec = ResponseSpec(
                    execute = executeSpec ?: defaultSpec.execute,
                    executeStreaming = executeStreamingSpec ?: defaultSpec.executeStreaming,
                    executeMultipleChoices = executeMultipleChoicesSpec ?: defaultSpec.executeMultipleChoices,
                    moderate = moderateSpec ?: defaultSpec.moderate
                )
            )
        }

        operator fun invoke(
            provider: LLMProvider = LLMProvider.OpenAI,
            executeContent: String? = null,
            executeStreamingContent: List<String>? = null,
            executeMultipleContent: List<LLMChoice>? = null,
            moderateContent: ModerationResult? = null,
        ): MockLLMClient {
            val defaultSpec = ResponseSpec.defaultSpec(provider)
            return MockLLMClient(
                provider = provider,
                responseSpec = ResponseSpec(
                    execute = executeContent?.let { executeSuccess(it) } ?: defaultSpec.execute,
                    executeStreaming = executeStreamingContent?.let { executeStreamingSuccess(*it.toTypedArray()) }
                        ?: defaultSpec.executeStreaming,
                    executeMultipleChoices = executeMultipleContent?.let { Result.success(it) }
                        ?: defaultSpec.executeMultipleChoices,
                    moderate = moderateContent?.let { Result.success(it) } ?: defaultSpec.moderate
                )
            )
        }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = executeResponse

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = executeStreamingResponse

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = executeMultipleChoicesResponse

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = moderateResponse

    override fun llmProvider(): LLMProvider = provider

    override suspend fun models(): List<LLModel> = models

    private var wasClosed = false
    fun wasClosed() = wasClosed

    override fun close() {
        wasClosed = true
    }

    val executeResponse: List<Message.Assistant>
        get() = responseSpec.execute.getOrThrow()

    val executeFailure: Throwable?
        get() = responseSpec.execute.exceptionOrNull()

    val executeStreamingResponse: Flow<StreamFrame>
        get() = responseSpec.executeStreaming.getOrThrow()

    val executeStreamingFailure: Throwable?
        get() = responseSpec.executeStreaming.exceptionOrNull()

    val executeMultipleChoicesResponse: List<LLMChoice>
        get() = responseSpec.executeMultipleChoices.getOrThrow()

    val executeMultipleChoicesFailure: Throwable?
        get() = responseSpec.executeMultipleChoices.exceptionOrNull()

    val moderateResponse: ModerationResult
        get() = responseSpec.moderate.getOrThrow()

    val moderateFailure: Throwable?
        get() = responseSpec.moderate.exceptionOrNull()
}
