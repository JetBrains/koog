package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.ExecutionCompleted
import ai.koog.prompt.executor.model.ExecutionDispatched
import ai.koog.prompt.executor.model.ExecutionFailed
import ai.koog.prompt.executor.model.ExecutionRequested
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.executor.model.ModerationCompleted
import ai.koog.prompt.executor.model.ModerationDispatched
import ai.koog.prompt.executor.model.ModerationFailed
import ai.koog.prompt.executor.model.ModerationRequested
import ai.koog.prompt.executor.model.MultipleChoicesCompleted
import ai.koog.prompt.executor.model.MultipleChoicesDispatched
import ai.koog.prompt.executor.model.MultipleChoicesFailed
import ai.koog.prompt.executor.model.MultipleChoicesRequested
import ai.koog.prompt.executor.model.PromptExecutionContext
import ai.koog.prompt.executor.model.PromptExecutorEvent
import ai.koog.prompt.executor.model.PromptExecutorHook
import ai.koog.prompt.executor.model.StreamingCompleted
import ai.koog.prompt.executor.model.StreamingDispatched
import ai.koog.prompt.executor.model.StreamingFailed
import ai.koog.prompt.executor.model.StreamingFrameReceived
import ai.koog.prompt.executor.model.StreamingRequested
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

abstract class HookablePromptExecutorTest<T : HookablePromptExecutor> {

    abstract fun failingExecutor(failure: Throwable): T
    abstract fun passingExecutor(): T

    private val prompt = prompt("test-prompt") {
        system("Test system message")
        user("Test message")
    }
    private val model = OpenAIModels.Chat.GPT4o
    private val tools = listOf(
        ToolDescriptor("Test tool 1", "Test tool description 1", listOf()),
        ToolDescriptor("Test tool 2", "Test tool description 2", listOf()),
    )

    open fun testExecuteEvents() = runTest {
        // Given
        val executor = passingExecutor()
        val captor = EventsCaptor()
        val context = PromptExecutionContext(executorHook = captor)

        // When
        val responses = executor.execute(prompt, model, tools, context)

        // Then
        assertContentEquals(
            listOf(
                ExecutionRequested(context.promptExecutionId, prompt, model, tools),
                ExecutionDispatched(context.promptExecutionId, prompt, model, tools),
                ExecutionCompleted(context.promptExecutionId, prompt, model, tools, responses)
            ),
            captor.capturedEvents()
        )
    }

    open fun testExecuteEventsOnFailure() = runTest {
        // Given
        val failure = Exception("Test failure")
        val executor = failingExecutor(failure)
        val captor = EventsCaptor()
        val context = PromptExecutionContext(executorHook = captor)

        // When
        val executionFailure = assertFails {
            executor.execute(prompt, model, tools, context)
        }

        // Then
        assertEquals(failure, executionFailure, "failingExecutor(failure) should fail with provider failure: $failure")
        assertContentEquals(
            listOf(
                ExecutionRequested(context.promptExecutionId, prompt, model, tools),
                ExecutionDispatched(context.promptExecutionId, prompt, model, tools),
                ExecutionFailed(context.promptExecutionId, prompt, model, tools, failure)
            ),
            captor.capturedEvents()
        )
    }

    open fun testExecuteStreamingEvents() = runTest {
        // Given
        val executor = passingExecutor()
        val captor = EventsCaptor()
        val context = PromptExecutionContext(executorHook = captor)

        // When
        val frames = executor.executeStreaming(prompt, model, tools, context).toList()

        // Then
        assertContentEquals(
            buildList {
                add(StreamingRequested(context.promptExecutionId, prompt, model, tools))
                add(StreamingDispatched(context.promptExecutionId, prompt, model, tools))
                frames.forEach { frame ->
                    add(StreamingFrameReceived(context.promptExecutionId, prompt, model, tools, frame))
                }
                add(StreamingCompleted(context.promptExecutionId, prompt, model, tools))
            },
            captor.capturedEvents()
        )
    }

    open fun testExecuteStreamingEventsOnFailure() = runTest {
        // Given
        val failure = Exception("Test failure")
        val executor = failingExecutor(failure)
        val captor = EventsCaptor()
        val context = PromptExecutionContext(executorHook = captor)

        // When
        val executionFailure = assertFails {
            executor.executeStreaming(prompt, model, tools, context).toList()
        }

        // Then
        assertEquals(failure, executionFailure, "failingExecutor(failure) should fail with provider failure: $failure")
        assertContentEquals(
            listOf(
                StreamingRequested(context.promptExecutionId, prompt, model, tools),
                StreamingDispatched(context.promptExecutionId, prompt, model, tools),
                StreamingFailed(context.promptExecutionId, prompt, model, tools, failure)
            ),
            captor.capturedEvents()
        )
    }

    open fun testExecuteMultipleChoicesEvents() = runTest {
        // Given
        val executor = passingExecutor()
        val captor = EventsCaptor()
        val context = PromptExecutionContext(executorHook = captor)

        // When
        val choices = executor.executeMultipleChoices(prompt, model, tools, context)

        // Then
        assertContentEquals(
            listOf(
                MultipleChoicesRequested(context.promptExecutionId, prompt, model, tools),
                MultipleChoicesDispatched(context.promptExecutionId, prompt, model, tools),
                MultipleChoicesCompleted(context.promptExecutionId, prompt, model, tools, choices)
            ),
            captor.capturedEvents()
        )
    }

    open fun testExecuteMultipleChoicesEventsOnFailure() = runTest {
        // Given
        val failure = Exception("Test failure")
        val executor = failingExecutor(failure)
        val captor = EventsCaptor()
        val context = PromptExecutionContext(executorHook = captor)

        // When
        val executionFailure = assertFails {
            executor.executeMultipleChoices(prompt, model, tools, context)
        }

        // Then
        assertEquals(failure, executionFailure, "failingExecutor(failure) should fail with provider failure: $failure")
        assertContentEquals(
            listOf(
                MultipleChoicesRequested(context.promptExecutionId, prompt, model, tools),
                MultipleChoicesDispatched(context.promptExecutionId, prompt, model, tools),
                MultipleChoicesFailed(context.promptExecutionId, prompt, model, tools, failure)
            ),
            captor.capturedEvents()
        )
    }

    open fun testModerationEvents() = runTest {
        // Given
        val executor = passingExecutor()
        val captor = EventsCaptor()
        val context = PromptExecutionContext(executorHook = captor)

        // When
        val result = executor.moderate(prompt, model, context)

        // Then
        assertContentEquals(
            listOf(
                ModerationRequested(context.promptExecutionId, prompt, model),
                ModerationDispatched(context.promptExecutionId, prompt, model),
                ModerationCompleted(context.promptExecutionId, prompt, model, result)
            ),
            captor.capturedEvents()
        )
    }

    open fun testModerationEventsOnFailure() = runTest {
        // Given
        val failure = Exception("Test failure")
        val executor = failingExecutor(failure)
        val captor = EventsCaptor()
        val context = PromptExecutionContext(executorHook = captor)

        // When
        val executionFailure = assertFails {
            executor.moderate(prompt, model, context)
        }

        // Then
        assertEquals(failure, executionFailure, "failingExecutor(failure) should fail with provider failure: $failure")
        assertContentEquals(
            listOf(
                ModerationRequested(context.promptExecutionId, prompt, model),
                ModerationDispatched(context.promptExecutionId, prompt, model),
                ModerationFailed(context.promptExecutionId, prompt, model, failure)
            ),
            captor.capturedEvents()
        )
    }

    open fun testUncaughtHookFailuresPropagation() = runTest {
        eventTypesByExecutionPath.forEach { (path, eventTypes) ->
            testUncaughtHookFailurePropagation(path, eventTypes)
        }
    }

    private suspend fun testUncaughtHookFailurePropagation(
        executionPath: ExecutionPath,
        eventTypes: List<KClass<out PromptExecutorEvent>>
    ) {
        val hookFailure = Exception("Hook failure")
        val passingExecutor = passingExecutor()
        val failingExecutor = failingExecutor(hookFailure)
        when (executionPath) {
            ExecutionPath.Execute -> eventTypes.forEach { eventType ->
                val executor = if (eventType == ExecutionFailed::class) failingExecutor else passingExecutor
                assertFailsWith(hookFailure) {
                    executor.execute(
                        prompt = prompt,
                        model = model,
                        tools = tools,
                        context = PromptExecutionContext(
                            executorHook = failingHook(
                                failFor = eventType,
                                failWith = hookFailure
                            )
                        )
                    )
                }
            }

            ExecutionPath.ExecuteMultipleChoices -> eventTypes.forEach { eventType ->
                val executor =
                    if (eventType == MultipleChoicesFailed::class) failingExecutor else passingExecutor
                assertFailsWith(hookFailure) {
                    executor.executeMultipleChoices(
                        prompt = prompt,
                        model = model,
                        tools = tools,
                        context = PromptExecutionContext(
                            executorHook = failingHook(
                                failFor = eventType,
                                failWith = hookFailure
                            )
                        )
                    )
                }
            }

            ExecutionPath.ExecuteStreaming -> eventTypes.forEach { eventType ->
                val executor =
                    if (eventType == StreamingFailed::class) failingExecutor else passingExecutor
                assertFailsWith(hookFailure) {
                    executor.executeStreaming(
                        prompt = prompt,
                        model = model,
                        tools = tools,
                        context = PromptExecutionContext(
                            executorHook = failingHook(
                                failFor = eventType,
                                failWith = hookFailure
                            )
                        )
                    ).collect()
                }
            }

            ExecutionPath.Moderate -> eventTypes.forEach { eventType ->
                val executor =
                    if (eventType == ModerationFailed::class) failingExecutor else passingExecutor
                assertFailsWith(hookFailure) {
                    executor.moderate(
                        prompt = prompt,
                        model = model,
                        context = PromptExecutionContext(
                            executorHook = failingHook(
                                failFor = eventType,
                                failWith = hookFailure
                            )
                        )
                    )
                }
            }
        }

    }

    private suspend fun assertFailsWith(expectedFailure: Throwable, block: suspend () -> Unit) {
        val failure = assertFails { block() }
        assertEquals(expectedFailure, failure, "Expected failure: $expectedFailure but got: $failure")
    }

    private fun failingHook(failFor: KClass<out PromptExecutorEvent>, failWith: Throwable): PromptExecutorHook {
        return PromptExecutorHook { event ->
            if (failFor.isInstance(event)) {
                throw failWith
            }
        }
    }

    private class EventsCaptor : PromptExecutorHook {

        private val events = mutableListOf<PromptExecutorEvent>()

        override suspend fun handle(event: PromptExecutorEvent) {
            events += event
        }

        fun capturedEvents(): List<PromptExecutorEvent> {
            return events.toList()
        }
    }

    companion object {
        private enum class ExecutionPath {
            Execute,
            ExecuteStreaming,
            ExecuteMultipleChoices,
            Moderate,
        }

        private val eventTypesByExecutionPath = mapOf(
            ExecutionPath.Execute to listOf(
                ExecutionRequested::class,
                ExecutionDispatched::class,
                ExecutionCompleted::class,
                ExecutionFailed::class
            ),
            ExecutionPath.ExecuteMultipleChoices to listOf(
                MultipleChoicesRequested::class,
                MultipleChoicesDispatched::class,
                MultipleChoicesCompleted::class,
                MultipleChoicesFailed::class
            ),
            ExecutionPath.ExecuteStreaming to listOf(
                StreamingRequested::class,
                StreamingDispatched::class,
                StreamingFrameReceived::class,
                StreamingCompleted::class,
                StreamingFailed::class,
            ),
            ExecutionPath.Moderate to listOf(
                ModerationRequested::class,
                ModerationDispatched::class,
                ModerationCompleted::class,
                ModerationFailed::class
            )
        )
    }
}
