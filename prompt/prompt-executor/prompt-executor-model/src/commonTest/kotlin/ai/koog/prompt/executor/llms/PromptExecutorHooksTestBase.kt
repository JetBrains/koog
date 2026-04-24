package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.testing.client.CapturingLLMClient
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.ExecuteHook
import ai.koog.prompt.executor.model.ExecutionArgOverrides
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
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract tests verifying the hook lifecycle for a [HookablePromptExecutor] implementation.
 *
 * Subclass once per executor and implement [createExecutor] and [model].
 * All hook lifecycle guarantees are then verified automatically:
 * - [beforeExecution] prompt overrides are forwarded to the LLM client
 * - [SimpleExecutorHook.onCompleted] receives the resolved intent and the LLM result
 * - [SimpleExecutorHook.onFailure] is called when the LLM throws, and the exception propagates
 * - [SimpleExecutorHook.onCompleted] is NOT called on failure (unlike streaming)
 * - [StreamingHook.onFrame] is called for every frame in order
 * - [StreamingHook.onCompleted] always fires — even after [StreamingHook.onFailure]
 * - `null` hooks never cause a crash
 */
abstract class PromptExecutorHooksTestBase {

    /** Returns the executor under test backed by [client]. */
    abstract fun createExecutor(client: LLMClient): HookablePromptExecutor

    /**
     * The model used for all test calls.
     * Its [LLModel.provider] must match the provider returned by any [LLMClient] created here,
     * so that routing executors can resolve the correct client.
     */
    abstract val model: LLModel

    private val originalPrompt = Prompt.build("original") { user("original") }
    private val overriddenPrompt = Prompt.build("overridden") { user("overridden") }
    private val someTools = listOf(ToolDescriptor("dummy", "Dummy tool", emptyList()))
    private val someResponse = listOf(Message.Assistant("ok", ResponseMetaInfo.Empty))
    private val someChoices = listOf(listOf(Message.Assistant("choice", ResponseMetaInfo.Empty)))
    private val someModerationResult = ModerationResult(isHarmful = false, categories = emptyMap())
    private val someFrames = listOf(
        StreamFrame.TextDelta("a"),
        StreamFrame.TextDelta("b"),
        StreamFrame.End("stop")
    )

    @Test
    fun testExecutePromptOverrideIsForwardedToLLM() = runTest {
        // Given
        val client = capturingClient()
        val executor = createExecutor(client)

        // And
        val hook = object : ExecuteHook {
            override suspend fun beforeExecution(intent: InitialExecutionIntent, effectiveModel: LLModel) =
                ExecutionArgOverrides.UseDifferentPrompt(overriddenPrompt)
        }

        // When
        executor.execute(prompt = originalPrompt, model = model, tools = someTools, hook = hook)

        // Then
        assertEquals(overriddenPrompt, client.lastExecutedPrompt)
    }

    @Test
    fun testExecuteOnCompletedReceivesResolvedIntentAndResult() = runTest {
        // Given
        val client = capturingClient()
        val executor = createExecutor(client)
        var completedIntent: ResolvedExecutionIntent? = null
        var completedResult: List<Message.Response>? = null

        // And
        val hook = object : ExecuteHook {
            override suspend fun beforeExecution(intent: InitialExecutionIntent, effectiveModel: LLModel) =
                ExecutionArgOverrides.UseDifferentPrompt(overriddenPrompt)

            override suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel, result: List<Message.Response>) {
                completedIntent = intent
                completedResult = result
            }
        }

        // When
        executor.execute(prompt = originalPrompt, model = model, hook = hook)

        // Then
        assertEquals(overriddenPrompt, completedIntent?.prompt, "onCompleted receives the resolved (overridden) prompt")
        assertEquals(model, completedIntent?.model, "onCompleted receives the original requested model")
        assertEquals(someResponse, completedResult)
    }

    @Test
    fun testExecuteOnFailureCalledAndExceptionPropagates() = runTest {
        // Given
        val executor = createExecutor(failingClient())
        var capturedError: Throwable? = null

        // And
        val hook = object : ExecuteHook {
            override suspend fun onFailure(intent: ResolvedExecutionIntent, effectiveModel: LLModel, error: Throwable) {
                capturedError = error
            }
        }

        // When
        val thrown = assertFailsWith<Throwable> {
            executor.execute(prompt = originalPrompt, model = model, hook = hook)
        }

        // Then
        assertEquals(thrown, capturedError, "onFailure must receive the same exception that propagated to the caller")
    }

    @Test
    fun testExecuteOnCompletedNotCalledOnFailure() = runTest {
        // Given
        val executor = createExecutor(failingClient())
        var completedCalled = false

        // And
        val hook = object : ExecuteHook {
            override suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel, result: List<Message.Response>) {
                completedCalled = true
            }
        }

        // When
        assertFailsWith<Throwable> {
            executor.execute(prompt = originalPrompt, model = model, hook = hook)
        }

        // Then
        assertFalse(completedCalled, "onCompleted must not be called when the LLM call fails")
    }

    @Test
    fun testExecuteWithNullHooksDoesNotCrash() = runTest {
        // Given
        val executor = createExecutor(capturingClient())

        // When
        val result = executor.execute(originalPrompt, model, someTools, hook = null)

        // Then
        assertEquals(someResponse, result)
    }

    @Test
    fun testStreamingPromptOverrideIsForwardedToLLM() = runTest {
        // Given
        val client = capturingClient()
        val executor = createExecutor(client)

        // And
        val hook = object : StreamingHook {
            override suspend fun beforeExecution(intent: InitialExecutionIntent, effectiveModel: LLModel) =
                ExecutionArgOverrides.UseDifferentPrompt(overriddenPrompt)
        }

        // When
        executor.executeStreaming(prompt = originalPrompt, model = model, tools = someTools, hook = hook).toList()

        // Then
        assertEquals(overriddenPrompt, client.lastStreamingPrompt)
    }

    @Test
    fun testStreamingOnFrameCalledForEachFrameInOrder() = runTest {
        // Given
        val executor = createExecutor(capturingClient())
        val capturedFrames = mutableListOf<StreamFrame>()

        // And
        val hook = object : StreamingHook {
            override suspend fun onFrame(intent: ResolvedExecutionIntent, effectiveModel: LLModel, frame: StreamFrame) {
                capturedFrames += frame
            }
        }

        // When
        executor.executeStreaming(prompt = originalPrompt, model = model, tools = someTools, hook = hook).toList()

        // Then
        assertEquals(someFrames, capturedFrames)
    }

    @Test
    fun testStreamingOnCompletedCalledAfterAllFrames() = runTest {
        // Given
        val executor = createExecutor(capturingClient())
        val events = mutableListOf<String>()

        // And
        val hook = object : StreamingHook {
            override suspend fun onFrame(intent: ResolvedExecutionIntent, effectiveModel: LLModel, frame: StreamFrame) {
                events += "frame"
            }

            override suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel) {
                events += "completed"
            }
        }

        // When
        executor.executeStreaming(prompt = originalPrompt, model = model, tools = someTools, hook = hook).toList()

        // Then
        assertEquals(someFrames.size, events.count { it == "frame" })
        assertEquals("completed", events.last(), "onCompleted must fire after all frames on success")
    }

    @Test
    fun testStreamingOnCompletedAlwaysFiresAfterFailure() = runTest {
        // Given
        val streamError = RuntimeException("stream error")
        val executor = createExecutor(throwingStreamClient(streamError))
        var onFailureCalled = false
        var onCompletedCalled = false

        // And
        val hook = object : StreamingHook {
            override suspend fun onFailure(intent: ResolvedExecutionIntent, effectiveModel: LLModel, error: Throwable) {
                onFailureCalled = true
            }

            override suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel) {
                onCompletedCalled = true
            }
        }

        // When
        assertFailsWith<RuntimeException> {
            executor.executeStreaming(prompt = originalPrompt, model = model, tools = someTools, hook = hook).toList()
        }

        // Then
        assertTrue(onFailureCalled, "onFailure must be called when the stream throws")
        assertTrue(onCompletedCalled, "onCompleted must always fire, even after onFailure")
    }

    @Test
    fun testStreamingWithNullHooksDoesNotCrash() = runTest {
        // Given
        val executor = createExecutor(capturingClient())

        // When
        val result = executor.executeStreaming(originalPrompt, model, someTools, hook = null).toList()

        // Then
        assertEquals(someFrames, result)
    }

    @Test
    fun testMultipleChoicesPromptOverrideIsForwardedToLLM() = runTest {
        // Given
        val client = capturingClient()
        val executor = createExecutor(client)

        // And
        val hook = object : MultipleChoicesHook {
            override suspend fun beforeExecution(intent: InitialExecutionIntent, effectiveModel: LLModel) =
                ExecutionArgOverrides.UseDifferentPrompt(overriddenPrompt)
        }

        // When
        executor.executeMultipleChoices(prompt = originalPrompt, model = model, tools = someTools, hook = hook)

        // Then
        assertEquals(overriddenPrompt, client.lastChoicesPrompt)
    }

    @Test
    fun testMultipleChoicesOnCompletedReceivesResult() = runTest {
        // Given
        val executor = createExecutor(capturingClient())
        var completedResult: List<LLMChoice>? = null

        // And
        val hook = object : MultipleChoicesHook {
            override suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel, result: List<LLMChoice>) {
                completedResult = result
            }
        }

        // When
        executor.executeMultipleChoices(prompt = originalPrompt, model = model, hook = hook)

        // Then
        assertEquals(someChoices, completedResult)
    }

    @Test
    fun testMultipleChoicesOnFailureCalledAndExceptionPropagates() = runTest {
        // Given
        val executor = createExecutor(failingClient())
        var capturedError: Throwable? = null

        // And
        val hook = object : MultipleChoicesHook {
            override suspend fun onFailure(intent: ResolvedExecutionIntent, effectiveModel: LLModel, error: Throwable) {
                capturedError = error
            }
        }

        // When
        val thrown = assertFailsWith<Throwable> {
            executor.executeMultipleChoices(prompt = originalPrompt, model = model, hook = hook)
        }

        // Then
        assertEquals(thrown, capturedError)
    }

    @Test
    fun testMultipleChoicesWithNullHooksDoesNotCrash() = runTest {
        // Given
        val executor = createExecutor(capturingClient())

        // When
        val result = executor.executeMultipleChoices(originalPrompt, model, someTools, hook = null)

        // Then
        assertEquals(someChoices, result)
    }

    @Test
    fun testModerationPromptOverrideIsForwardedToLLM() = runTest {
        // Given
        val client = capturingClient()
        val executor = createExecutor(client)

        // And
        val hook = object : ModerateHook {
            override suspend fun beforeExecution(intent: InitialExecutionIntent, effectiveModel: LLModel) =
                ExecutionArgOverrides.UseDifferentPrompt(overriddenPrompt)
        }

        // When
        executor.moderate(prompt = originalPrompt, model = model, hook = hook)

        // Then
        assertEquals(overriddenPrompt, client.lastModerationPrompt)
    }

    @Test
    fun testModerationOnCompletedReceivesResult() = runTest {
        // Given
        val executor = createExecutor(capturingClient())
        var completedResult: ModerationResult? = null

        // And
        val hook = object : ModerateHook {
            override suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel, result: ModerationResult) {
                completedResult = result
            }
        }

        // When
        executor.moderate(prompt = originalPrompt, model = model, hook = hook)

        // Then
        assertEquals(someModerationResult, completedResult)
    }

    @Test
    fun testModerationOnFailureCalledAndExceptionPropagates() = runTest {
        // Given
        val executor = createExecutor(failingClient())
        var capturedError: Throwable? = null

        // And
        val hook = object : ModerateHook {
            override suspend fun onFailure(intent: ResolvedExecutionIntent, effectiveModel: LLModel, error: Throwable) {
                capturedError = error
            }
        }

        // When
        val thrown = assertFailsWith<Throwable> {
            executor.moderate(prompt = originalPrompt, model = model, hook = hook)
        }

        // Then
        assertEquals(thrown, capturedError)
    }

    @Test
    fun testModerationWithNullHooksDoesNotCrash() = runTest {
        // Given
        val executor = createExecutor(capturingClient())

        // When
        val result = executor.moderate(originalPrompt, model, hook = null)

        // Then
        assertEquals(someModerationResult, result)
    }

    private fun capturingClient() = CapturingLLMClient(
        executeResponses = someResponse,
        streamingChunks = someFrames,
        choices = someChoices,
        moderationResult = someModerationResult,
        llmProvider = model.provider
    )

    /** Client that throws on every operation. */
    private fun failingClient() = MockLLMClient.failingClientMock(model.provider)

    /** Client whose streaming flow throws [error] when collected. */
    private fun throwingStreamClient(error: Throwable) = MockLLMClient(
        provider = model.provider,
        executeStreamingSpec = Result.success(flow<StreamFrame> { throw error })
    )
}
