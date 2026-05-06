package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.ExecutionCompleted
import ai.koog.prompt.executor.model.ExecutionDispatched
import ai.koog.prompt.executor.model.ExecutionFailed
import ai.koog.prompt.executor.model.ExecutionRequested
import ai.koog.prompt.executor.model.ModerationCompleted
import ai.koog.prompt.executor.model.ModerationDispatched
import ai.koog.prompt.executor.model.ModerationFailed
import ai.koog.prompt.executor.model.ModerationRequested
import ai.koog.prompt.executor.model.MultipleChoicesCompleted
import ai.koog.prompt.executor.model.MultipleChoicesDispatched
import ai.koog.prompt.executor.model.MultipleChoicesFailed
import ai.koog.prompt.executor.model.MultipleChoicesRequested
import ai.koog.prompt.executor.model.ObservablePromptExecutor
import ai.koog.prompt.executor.model.PromptExecutionContext
import ai.koog.prompt.executor.model.PromptExecutorEvent
import ai.koog.prompt.executor.model.StreamingCompleted
import ai.koog.prompt.executor.model.StreamingDispatched
import ai.koog.prompt.executor.model.StreamingFailed
import ai.koog.prompt.executor.model.StreamingFrameReceived
import ai.koog.prompt.executor.model.StreamingRequested
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

abstract class PromptExecutorObservabilityTest<T: ObservablePromptExecutor> {

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
        val context = PromptExecutionContext()
        val captor = EventsCaptor(executor, backgroundScope)

        // When
        val responses = executor.execute(prompt, model, tools, context)

        // Then
        assertContentEquals(listOf(
            ExecutionRequested(context, prompt, model, tools),
            ExecutionDispatched(context, prompt, model, tools),
            ExecutionCompleted(context, prompt, model, tools, responses)
        ), captor.capturedEvents())
    }

    open fun testExecuteEventsOnFailure() = runTest {
        // Given
        val failure = Exception("Test failure")
        val executor = failingExecutor(failure)
        val context = PromptExecutionContext()
        val captor = EventsCaptor(executor, backgroundScope)

        // When
        val executionFailure = assertFails {
            executor.execute(prompt, model, tools, context)
        }

        // Then
        assertEquals(failure, executionFailure, "failingExecutor(failure) should fail with provider failure: $failure")
        assertContentEquals(listOf(
            ExecutionRequested(context, prompt, model, tools),
            ExecutionDispatched(context, prompt, model, tools),
            ExecutionFailed(context, prompt, model, tools, failure)
        ), captor.capturedEvents())
    }

    open fun testExecuteStreamingEvents() = runTest {
        // Given
        val executor = passingExecutor()
        val context = PromptExecutionContext()
        val captor = EventsCaptor(executor, backgroundScope)

        // When
        val frames = executor.executeStreaming(prompt, model, tools, context).toList()

        // Then
        assertContentEquals(
            buildList {
                add(StreamingRequested(context, prompt, model, tools))
                add(StreamingDispatched(context, prompt, model, tools))
                frames.forEach { frame ->
                    add(StreamingFrameReceived(context, prompt, model, tools, frame))
                }
                add(StreamingCompleted(context, prompt, model, tools))
            },
            captor.capturedEvents()
        )
    }

    open fun testExecuteStreamingEventsOnFailure() = runTest {
        // Given
        val failure = Exception("Test failure")
        val executor = failingExecutor(failure)
        val context = PromptExecutionContext()
        val captor = EventsCaptor(executor, backgroundScope)

        // When
        val executionFailure = assertFails {
            executor.executeStreaming(prompt, model, tools, context).toList()
        }

        // Then
        assertEquals(failure, executionFailure, "failingExecutor(failure) should fail with provider failure: $failure")
        assertContentEquals(listOf(
            StreamingRequested(context, prompt, model, tools),
            StreamingDispatched(context, prompt, model, tools),
            StreamingFailed(context, prompt, model, tools, failure)
        ), captor.capturedEvents())
    }

    open fun testExecuteMultipleChoicesEvents() = runTest {
        // Given
        val executor = passingExecutor()
        val context = PromptExecutionContext()
        val captor = EventsCaptor(executor, backgroundScope)

        // When
        val choices = executor.executeMultipleChoices(prompt, model, tools, context)

        // Then
        assertContentEquals(listOf(
            MultipleChoicesRequested(context, prompt, model, tools),
            MultipleChoicesDispatched(context, prompt, model, tools),
            MultipleChoicesCompleted(context, prompt, model, tools, choices)
        ), captor.capturedEvents())
    }

    open fun testExecuteMultipleChoicesEventsOnFailure() = runTest {
        // Given
        val failure = Exception("Test failure")
        val executor = failingExecutor(failure)
        val context = PromptExecutionContext()
        val captor = EventsCaptor(executor, backgroundScope)

        // When
        val executionFailure = assertFails {
            executor.executeMultipleChoices(prompt, model, tools, context)
        }

        // Then
        assertEquals(failure, executionFailure, "failingExecutor(failure) should fail with provider failure: $failure")
        assertContentEquals(listOf(
            MultipleChoicesRequested(context, prompt, model, tools),
            MultipleChoicesDispatched(context, prompt, model, tools),
            MultipleChoicesFailed(context, prompt, model, tools, failure)
        ), captor.capturedEvents())
    }

    open fun testModerationEvents() = runTest {
        // Given
        val executor = passingExecutor()
        val context = PromptExecutionContext()
        val captor = EventsCaptor(executor, backgroundScope)

        // When
        val result = executor.moderate(prompt, model, context)

        // Then
        assertContentEquals(listOf(
            ModerationRequested(context, prompt, model),
            ModerationDispatched(context, prompt, model),
            ModerationCompleted(context, prompt, model, result)
        ), captor.capturedEvents())
    }

    open fun testModerationEventsOnFailure() = runTest {
        // Given
        val failure = Exception("Test failure")
        val executor = failingExecutor(failure)
        val context = PromptExecutionContext()
        val captor = EventsCaptor(executor, backgroundScope)

        // When
        val executionFailure = assertFails {
            executor.moderate(prompt, model, context)
        }

        // Then
        assertEquals(failure, executionFailure, "failingExecutor(failure) should fail with provider failure: $failure")
        assertContentEquals(listOf(
            ModerationRequested(context, prompt, model),
            ModerationDispatched(context, prompt, model),
            ModerationFailed(context, prompt, model, failure)
        ), captor.capturedEvents())
    }

    private class EventsCaptor(
        private val observablePromptExecutor: ObservablePromptExecutor,
        private val scope: CoroutineScope,
    ) {

        private val events = mutableListOf<PromptExecutorEvent>()

        init {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                observablePromptExecutor.events.collect {
                    events += it
                }
            }
        }

        fun capturedEvents(): List<PromptExecutorEvent> {
            return events.toList()
        }
    }
}
