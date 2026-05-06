package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PromptExecutorEventTest {

    @Test
    fun testRequestedAndDispatchedEventsCanCarryDifferentSnapshots() {
        val promptExecutionId = "prompt-execution-id"
        val context = PromptExecutionContext(promptExecutionId)
        val requestedPrompt = Prompt.Empty
        val dispatchedPrompt = Prompt(emptyList(), "dispatched")
        val requestedModel = LLModel(LLMProvider.OpenAI, "requested-model")
        val dispatchedModel = LLModel(LLMProvider.OpenAI, "dispatched-model")
        val requestedTools = listOf(ToolDescriptor("requested-tool", "Requested tool"))
        val dispatchedTools = listOf(ToolDescriptor("dispatched-tool", "Dispatched tool"))

        val requested = ExecutionRequested(context, requestedPrompt, requestedModel, requestedTools)
        val dispatched = ExecutionDispatched(context, dispatchedPrompt, dispatchedModel, dispatchedTools)

        assertEquals(requested.context.promptExecutionId, dispatched.context.promptExecutionId)
        assertEquals(requestedPrompt, requested.prompt)
        assertEquals(dispatchedPrompt, dispatched.prompt)
        assertEquals(requestedModel, requested.model)
        assertEquals(dispatchedModel, dispatched.model)
        assertEquals(requestedTools, requested.tools)
        assertEquals(dispatchedTools, dispatched.tools)
    }

    @Test
    fun testTerminalEventsCarryOperationResults() {
        val prompt = Prompt.Empty
        val model = LLModel(LLMProvider.OpenAI, "model")
        val tools = listOf(ToolDescriptor("tool", "Tool"))
        val response = Message.Assistant("response", ResponseMetaInfo.Empty)
        val choices = listOf(listOf(response))
        val moderationResult = ModerationResult(isHarmful = false, categories = emptyMap())

        val executionCompleted = ExecutionCompleted(
            PromptExecutionContext("execution"),
            prompt,
            model,
            tools,
            listOf(response)
        )
        val choicesCompleted = MultipleChoicesCompleted(PromptExecutionContext("choices"), prompt, model, tools, choices)
        val moderationCompleted = ModerationCompleted(PromptExecutionContext("moderation"), prompt, model, moderationResult)

        assertEquals(listOf(response), executionCompleted.responses)
        assertEquals(choices, choicesCompleted.choices)
        assertSame(moderationResult, moderationCompleted.result)
    }

    @Test
    fun testStreamingFrameReceivedIsPromptExecutorEvent() {
        val frame = StreamFrame.TextDelta("delta")
        val event = StreamingFrameReceived(
            context = PromptExecutionContext("streaming"),
            prompt = Prompt.Empty,
            model = LLModel(LLMProvider.OpenAI, "model"),
            tools = emptyList(),
            frame = frame
        )

        assertIs<PromptExecutorEvent>(event)
        assertEquals(frame, event.frame)
    }
}
