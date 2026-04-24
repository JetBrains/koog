@file:OptIn(InternalAgentsApi::class, DetachedPromptExecutorAPI::class)

package ai.koog.agents.feature

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.ContextualPromptExecutor
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.feature.ContextualPromptExecutorTest.CapturedPipelineCall.Args
import ai.koog.agents.feature.ContextualPromptExecutorTest.CapturedPipelineCall.CallbackType.ON_LLM_CALL_COMPLETED
import ai.koog.agents.feature.ContextualPromptExecutorTest.CapturedPipelineCall.CallbackType.ON_LLM_CALL_STARTING
import ai.koog.agents.feature.ContextualPromptExecutorTest.CapturedPipelineCall.CallbackType.ON_LLM_STREAMING_COMPLETED
import ai.koog.agents.feature.ContextualPromptExecutorTest.CapturedPipelineCall.CallbackType.ON_LLM_STREAMING_FAILED
import ai.koog.agents.feature.ContextualPromptExecutorTest.CapturedPipelineCall.CallbackType.ON_LLM_STREAMING_FRAME_RECEIVED
import ai.koog.agents.feature.ContextualPromptExecutorTest.CapturedPipelineCall.CallbackType.ON_LLM_STREAMING_STARTING
import ai.koog.agents.testing.tools.AIAgentContextMockBuilder
import ai.koog.agents.testing.tools.DummyAIAgentContext
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4o
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT5Pro
import ai.koog.prompt.executor.model.ExecuteHook
import ai.koog.prompt.executor.model.ExecutionArgOverrides.NoOverrides
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.ModerateHook
import ai.koog.prompt.executor.model.MultipleChoicesHook
import ai.koog.prompt.executor.model.ResolvedExecutionIntent
import ai.koog.prompt.executor.model.SimpleExecutorHook
import ai.koog.prompt.executor.model.StreamingHook
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock

class ContextualPromptExecutorTest {

    private val agentConfig = AIAgentConfig(
        prompt = prompt("test-prompt") { system("You're a helpful assistant.") },
        model = AnthropicModels.Opus_4_6,
        maxAgentIterations = 5
    )

    private val testEnvironment = object : AIAgentEnvironment {
        override suspend fun executeTool(toolCall: Message.Tool.Call) =
            throw UnsupportedOperationException()

        override suspend fun reportProblem(exception: Throwable) {}
    }

    private val testPrompt = prompt("user-prompt") { user("Hello, how are you?") }

    @Test
    fun testExecutePipelineIntegration() = runTest {
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(TestHookPromptExecutor(), context)

        contextualExecutor.execute(testPrompt, GPT4o)

        assertPipelineCalls(
            capturingPipeline, listOf(
                ExpectedPipelineCall(
                    callbackType = ON_LLM_CALL_STARTING,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT4o,
                    originContext = context
                ),
                ExpectedPipelineCall(
                    callbackType = ON_LLM_CALL_COMPLETED,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT4o,
                    originContext = context
                ),
            )
        )
    }

    @Test
    fun testExecuteReportsEffectiveModelWhenSubstituted() = runTest {
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(
            TestHookPromptExecutor(effectiveModel = GPT5Pro), context
        )

        contextualExecutor.execute(testPrompt, GPT4o)

        assertPipelineCalls(
            capturingPipeline, listOf(
                ExpectedPipelineCall(
                    callbackType = ON_LLM_CALL_STARTING,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT5Pro,
                    originContext = context
                ),
                ExpectedPipelineCall(
                    callbackType = ON_LLM_CALL_COMPLETED,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT5Pro,
                    originContext = context
                ),
            )
        )
    }

    @Test
    fun testExecutePipelineFailure() = runTest {
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(
            TestHookPromptExecutor(executionFailure = RuntimeException("test-failure")), context
        )

        assertFailsWith<RuntimeException> {
            contextualExecutor.execute(testPrompt, GPT4o)
        }

        assertPipelineCalls(
            capturingPipeline, listOf(
                ExpectedPipelineCall(
                    callbackType = ON_LLM_CALL_STARTING,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT4o,
                    originContext = context
                ),
            )
        )
    }

    @Test
    fun testStreamingPipelineIntegration() = runTest {
        val frames = listOf(StreamFrame.TextDelta("Hello"), StreamFrame.TextDelta(" world"))
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(TestHookPromptExecutor(streamFrames = frames), context)

        contextualExecutor.executeStreaming(testPrompt, GPT4o).toList()

        assertPipelineCalls(
            capturingPipeline, listOf(
                ExpectedPipelineCall(
                    callbackType = ON_LLM_STREAMING_STARTING,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT4o,
                    originContext = context
                ),
                ExpectedPipelineCall(
                    callbackType = ON_LLM_STREAMING_FRAME_RECEIVED,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT4o,
                    originContext = context
                ),
                ExpectedPipelineCall(
                    callbackType = ON_LLM_STREAMING_FRAME_RECEIVED,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT4o,
                    originContext = context
                ),
                ExpectedPipelineCall(
                    callbackType = ON_LLM_STREAMING_COMPLETED,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT4o,
                    originContext = context
                ),
            )
        )
    }

    @Test
    fun testStreamingReportsEffectiveModelWhenSubstituted() = runTest {
        val frames = listOf(StreamFrame.TextDelta("Hello"))
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(
            TestHookPromptExecutor(effectiveModel = GPT5Pro, streamFrames = frames), context
        )

        contextualExecutor.executeStreaming(testPrompt, GPT4o).toList()

        assertPipelineCalls(
            capturingPipeline, listOf(
                ExpectedPipelineCall(
                    callbackType = ON_LLM_STREAMING_STARTING,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT5Pro,
                    originContext = context
                ),
                ExpectedPipelineCall(
                    callbackType = ON_LLM_STREAMING_FRAME_RECEIVED,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT5Pro,
                    originContext = context
                ),
                ExpectedPipelineCall(
                    callbackType = ON_LLM_STREAMING_COMPLETED,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT5Pro,
                    originContext = context
                ),
            )
        )
    }

    @Test
    fun testStreamingPipelineFailure() = runTest {
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(
            TestHookPromptExecutor(executionFailure = RuntimeException("stream-failure")), context
        )

        assertFailsWith<RuntimeException> {
            contextualExecutor.executeStreaming(testPrompt, GPT4o).toList()
        }

        assertPipelineCalls(
            capturingPipeline, listOf(
                ExpectedPipelineCall(ON_LLM_STREAMING_STARTING, testPrompt, GPT4o, context),
                ExpectedPipelineCall(ON_LLM_STREAMING_FAILED, testPrompt, GPT4o, context),
                ExpectedPipelineCall(ON_LLM_STREAMING_COMPLETED, testPrompt, GPT4o, context),
            )
        )
    }

    @Test
    fun testMultipleChoicesPipelineIntegration() = runTest {
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(TestHookPromptExecutor(), context)

        contextualExecutor.executeMultipleChoices(testPrompt, GPT4o)

        // executeMultipleChoices has no pipeline integration yet (ignorePipeline = true)
        assertPipelineCalls(capturingPipeline, emptyList())
    }

    @Test
    fun testExecuteModelChoiceFailure() = runTest {
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(
            TestHookPromptExecutor(modelChoiceFailure = RuntimeException("no-client")), context
        )

        assertFailsWith<RuntimeException> {
            contextualExecutor.execute(testPrompt, GPT4o)
        }

        // onModelChoiceFailed has no pipeline callback — beforeExecution (and STARTING) is never reached
        assertPipelineCalls(capturingPipeline, emptyList())
    }

    @Test
    fun testStreamingModelChoiceFailure() = runTest {
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(
            TestHookPromptExecutor(modelChoiceFailure = RuntimeException("no-client")), context
        )

        assertFailsWith<RuntimeException> {
            contextualExecutor.executeStreaming(testPrompt, GPT4o).toList()
        }

        assertPipelineCalls(capturingPipeline, emptyList())
    }

    @Test
    fun testPromptOverrideViaFeatureInterceptor() = runTest {
        val modifiedPrompt = prompt("modified-prompt") { user("I was modified by a feature") }
        val modifyingPipeline = PromptModifyingPipeline(agentConfig, modifiedPrompt)
        val context = agentContext(modifyingPipeline)
        val contextualExecutor = ContextualPromptExecutor(TestHookPromptExecutor(), context)

        contextualExecutor.execute(testPrompt, GPT4o)

        assertPipelineCalls(
            modifyingPipeline, listOf(
                // STARTING sees the original prompt (captured before interceptor mutated context.llm.prompt)
                ExpectedPipelineCall(
                    callbackType = ON_LLM_CALL_STARTING,
                    effectivePrompt = testPrompt,
                    effectiveModel = GPT4o,
                    originContext = context
                ),
                // COMPLETED sees the overridden prompt (what was actually forwarded to the model)
                ExpectedPipelineCall(
                    callbackType = ON_LLM_CALL_COMPLETED,
                    effectivePrompt = modifiedPrompt,
                    effectiveModel = GPT4o,
                    originContext = context
                ),
            )
        )
    }

    @Test
    fun testModeratePipelineIntegration() = runTest {
        val capturingPipeline = CapturingPipeline(agentConfig)
        val context = agentContext(capturingPipeline)
        val contextualExecutor = ContextualPromptExecutor(TestHookPromptExecutor(), context)

        contextualExecutor.moderate(testPrompt, GPT4o)

        assertPipelineCalls(
            capturingPipeline, listOf(
                ExpectedPipelineCall(ON_LLM_CALL_STARTING, testPrompt, GPT4o, context),
                ExpectedPipelineCall(ON_LLM_CALL_COMPLETED, testPrompt, GPT4o, context),
            )
        )
        assertEquals(
            ModerationResult(false, emptyMap()),
            capturingPipeline.getCapturedCalls()[1].args.moderationResponse
        )
    }

    private open class ExpectedPipelineCall(
        val callbackType: CapturedPipelineCall.CallbackType,
        val effectivePrompt: Prompt,
        val effectiveModel: LLModel,
        val originContext: AIAgentContext,
        val responses: List<Message.Response>? = null,
        val frameReceived: StreamFrame? = null,
        val moderationResponse: ModerationResult? = null,
    ) {

        open fun assertMatches(capturedCall: CapturedPipelineCall) {
            assertEquals(callbackType, capturedCall.callbackType)
            assertEquals(effectivePrompt, capturedCall.args.prompt)
            assertEquals(effectiveModel, capturedCall.args.model)
            assertEquals(originContext.executionInfo, capturedCall.args.executionInfo)
            assertEquals(originContext.runId, capturedCall.args.runId)
            assertEquals(originContext, capturedCall.args.context)
            assertEquals(responses, capturedCall.args.responses)
            assertEquals(frameReceived, capturedCall.args.streamFrame)
            assertEquals(moderationResponse, capturedCall.args.moderationResponse)
        }
    }

    private fun assertPipelineCalls(capturingPipeline: CapturingPipeline, callAssertions: List<ExpectedPipelineCall>) {
        val capturedCalls = capturingPipeline.getCapturedCalls()
        assertEquals(callAssertions.size, capturedCalls.size)
        capturedCalls.forEachIndexed { index, call ->
            callAssertions[index].assertMatches(call)
        }
    }

    private class PromptModifyingPipeline(
        agentConfig: AIAgentConfig,
        private val modifiedPrompt: Prompt
    ) : CapturingPipeline(agentConfig) {
        override suspend fun onLLMCallStarting(
            eventId: String,
            executionInfo: AgentExecutionInfo,
            runId: String,
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            context: AIAgentContext
        ) {
            super.onLLMCallStarting(eventId, executionInfo, runId, prompt, model, tools, context)
            context.llm.prompt = modifiedPrompt
        }
    }

    private fun agentContext(capturingPipeline: CapturingPipeline): AIAgentContext {
        val builder = AIAgentContextMockBuilder().apply {
            config = agentConfig
            executionInfo = AgentExecutionInfo(null, "test-agent")
            llm = AIAgentLLMContext(
                tools = emptyList(),
                prompt = agentConfig.prompt,
                model = agentConfig.model,
                responseProcessor = null,
                promptExecutor = TestHookPromptExecutor(),
                environment = testEnvironment,
                config = agentConfig,
                clock = Clock.System
            )
        }
        return DummyAIAgentContext(builder, pipeline = capturingPipeline)
    }

    private data class CapturedPipelineCall(
        val callbackType: CallbackType,
        val args: Args
    ) {

        enum class CallbackType {
            ON_LLM_CALL_STARTING,
            ON_LLM_CALL_COMPLETED,
            ON_LLM_STREAMING_STARTING,
            ON_LLM_STREAMING_FRAME_RECEIVED,
            ON_LLM_STREAMING_COMPLETED,
            ON_LLM_STREAMING_FAILED,
        }

        data class Args(
            val eventId: String,
            val executionInfo: AgentExecutionInfo,
            val runId: String,
            val prompt: Prompt,
            val model: LLModel,
            val tools: List<ToolDescriptor>,
            val responses: List<Message.Response>? = null,
            val moderationResponse: ModerationResult? = null,
            val streamFrame: StreamFrame? = null,
            val context: AIAgentContext
        )
    }

    private open class CapturingPipeline(agentConfig: AIAgentConfig) :
        AIAgentGraphPipeline(agentConfig = agentConfig, clock = Clock.System) {

        private val capturedCalls = mutableListOf<CapturedPipelineCall>()

        fun getCapturedCalls(): List<CapturedPipelineCall> = capturedCalls.toList()

        override suspend fun onLLMCallStarting(
            eventId: String,
            executionInfo: AgentExecutionInfo,
            runId: String,
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            context: AIAgentContext
        ) = captureCall(
            ON_LLM_CALL_STARTING, Args(
                eventId = eventId,
                executionInfo = executionInfo,
                runId = runId,
                prompt = prompt,
                model = model,
                tools = tools,
                context = context
            )
        )

        override suspend fun onLLMCallCompleted(
            eventId: String,
            executionInfo: AgentExecutionInfo,
            runId: String,
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            responses: List<Message.Response>,
            moderationResponse: ModerationResult?,
            context: AIAgentContext
        ) = captureCall(
            ON_LLM_CALL_COMPLETED, Args(
                eventId = eventId,
                executionInfo = executionInfo,
                runId = runId,
                prompt = prompt,
                model = model,
                tools = tools,
                responses = responses,
                moderationResponse = moderationResponse,
                context = context
            )
        )

        override suspend fun onLLMStreamingStarting(
            eventId: String,
            executionInfo: AgentExecutionInfo,
            runId: String,
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            context: AIAgentContext
        ) = captureCall(
            ON_LLM_STREAMING_STARTING, Args(
                eventId = eventId,
                executionInfo = executionInfo,
                runId = runId,
                prompt = prompt,
                model = model,
                tools = tools,
                responses = emptyList(),
                moderationResponse = null,
                context = context
            )
        )

        override suspend fun onLLMStreamingFrameReceived(
            eventId: String,
            executionInfo: AgentExecutionInfo,
            runId: String,
            prompt: Prompt,
            model: LLModel,
            streamFrame: StreamFrame,
            context: AIAgentContext
        ) = captureCall(
            ON_LLM_STREAMING_FRAME_RECEIVED,
            Args(
                eventId = eventId,
                executionInfo = executionInfo,
                runId = runId,
                prompt = prompt,
                model = model,
                tools = emptyList(),
                responses = emptyList(),
                moderationResponse = null,
                context = context
            )
        )

        override suspend fun onLLMStreamingCompleted(
            eventId: String,
            executionInfo: AgentExecutionInfo,
            runId: String,
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            context: AIAgentContext
        ) = captureCall(
            ON_LLM_STREAMING_COMPLETED, Args(
                eventId = eventId,
                executionInfo = executionInfo,
                runId = runId,
                prompt = prompt,
                model = model,
                tools = tools,
                responses = emptyList(),
                moderationResponse = null,
                context = context
            )
        )

        override suspend fun onLLMStreamingFailed(
            eventId: String,
            executionInfo: AgentExecutionInfo,
            runId: String,
            prompt: Prompt,
            model: LLModel,
            throwable: Throwable,
            context: AIAgentContext
        ) = captureCall(
            ON_LLM_STREAMING_FAILED, Args(
                eventId = eventId,
                executionInfo = executionInfo,
                runId = runId,
                prompt = prompt,
                model = model,
                tools = emptyList(),
                responses = emptyList(),
                moderationResponse = null,
                context = context
            )
        )

        private fun captureCall(callbackType: CapturedPipelineCall.CallbackType, args: Args) {
            capturedCalls += CapturedPipelineCall(callbackType, args)
        }
    }

    private class TestHookPromptExecutor(
        val modelChoiceFailure: Throwable? = null,
        val executionFailure: Throwable? = null,
        val effectiveModel: LLModel? = null,
        val streamFrames: List<StreamFrame>? = null,
        val responses: List<Message.Response>? = null,
        val moderationResponse: ModerationResult? = null,
    ) : HookablePromptExecutor() {

        private suspend fun <T> basicFlow(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            hook: SimpleExecutorHook<T>?,
            resultBlock: suspend () -> T
        ): T {
            val resolvedModel = effectiveModel ?: model
            val initialIntent = InitialExecutionIntent(prompt, tools, model)
            if (modelChoiceFailure != null) {
                hook?.onModelChoiceFailed(initialIntent, modelChoiceFailure)
                throw modelChoiceFailure
            }
            val override = hook?.beforeExecution(initialIntent, resolvedModel) ?: NoOverrides
            val finalIntent = ResolvedExecutionIntent(initialIntent, override)
            return if (executionFailure != null) {
                hook?.onFailure(finalIntent, resolvedModel, executionFailure)
                throw executionFailure
            } else {
                val result = resultBlock()
                hook?.onCompleted(finalIntent, resolvedModel, result)
                result
            }
        }

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            hook: ExecuteHook?
        ): List<Message.Response> = basicFlow(prompt, model, tools, hook) {
            requireNotNull(responses) { "Stub responses for non-error execution flow" }
        }

        override suspend fun executeMultipleChoices(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            hook: MultipleChoicesHook?
        ): List<LLMChoice> = basicFlow(prompt, model, tools, hook) {
            listOf(requireNotNull(responses) { "Stub responses for non-error execution flow" })
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            hook: StreamingHook?
        ): Flow<StreamFrame> = flow {
            val resolvedModel = effectiveModel ?: model
            val initialIntent = InitialExecutionIntent(prompt, tools, model)
            if (modelChoiceFailure != null) {
                hook?.onModelChoiceFailed(initialIntent, modelChoiceFailure)
                throw modelChoiceFailure
            }
            val override = hook?.beforeExecution(initialIntent, resolvedModel) ?: NoOverrides
            val finalIntent = ResolvedExecutionIntent(initialIntent, override)
            if (executionFailure != null) {
                hook?.onFailure(finalIntent, resolvedModel, executionFailure)
                hook?.onCompleted(finalIntent, resolvedModel)
                throw executionFailure
            } else {
                requireNotNull(streamFrames) { "Stub stream frames for non-error streaming flow" }
                streamFrames.forEach {
                    emit(it)
                    hook?.onFrame(finalIntent, resolvedModel, it)
                }
                hook?.onCompleted(finalIntent, resolvedModel)
            }
        }

        override suspend fun moderate(
            prompt: Prompt,
            model: LLModel,
            hook: ModerateHook?
        ): ModerationResult = basicFlow(prompt, model, emptyList(), hook) {
            requireNotNull(moderationResponse) { "Stub moderation response for non-error moderation flow" }
        }

        override fun close() {}
    }
}
