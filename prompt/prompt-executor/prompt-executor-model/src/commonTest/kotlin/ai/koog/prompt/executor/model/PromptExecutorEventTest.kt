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
    fun testRequestedAndSubmittedEventsCanCarryDifferentSnapshots() {
        val promptExecutionId = "prompt-execution-id"
        val requestedPrompt = Prompt.Empty
        val submittedPrompt = Prompt(emptyList(), "submitted")
        val requestedModel = LLModel(LLMProvider.OpenAI, "requested-model")
        val submittedModel = LLModel(LLMProvider.OpenAI, "submitted-model")
        val requestedTools = listOf(ToolDescriptor("requested-tool", "Requested tool"))
        val submittedTools = listOf(ToolDescriptor("submitted-tool", "Submitted tool"))

        val requested = ExecutionRequested(promptExecutionId, requestedPrompt, requestedModel, requestedTools)
        val submitted = ExecutionSubmitted(promptExecutionId, submittedPrompt, submittedModel, submittedTools)

        assertEquals(requested.promptExecutionId, submitted.promptExecutionId)
        assertEquals(requestedPrompt, requested.prompt)
        assertEquals(submittedPrompt, submitted.prompt)
        assertEquals(requestedModel, requested.model)
        assertEquals(submittedModel, submitted.model)
        assertEquals(requestedTools, requested.tools)
        assertEquals(submittedTools, submitted.tools)
    }

    @Test
    fun testTerminalEventsCarryOperationResults() {
        val prompt = Prompt.Empty
        val model = LLModel(LLMProvider.OpenAI, "model")
        val tools = listOf(ToolDescriptor("tool", "Tool"))
        val response = Message.Assistant("response", ResponseMetaInfo.Empty)
        val choices = listOf(listOf(response))
        val moderationResult = ModerationResult(isHarmful = false, categories = emptyMap())

        val executionCompleted = ExecutionCompleted("execution", prompt, model, tools, listOf(response))
        val choicesCompleted = MultipleChoicesCompleted("choices", prompt, model, tools, choices)
        val moderationCompleted = ModerationCompleted("moderation", prompt, model, moderationResult)

        assertEquals(listOf(response), executionCompleted.responses)
        assertEquals(choices, choicesCompleted.choices)
        assertSame(moderationResult, moderationCompleted.result)
    }

    @Test
    fun testStreamingFrameReceivedIsPromptExecutorEvent() {
        val frame = StreamFrame.TextDelta("delta")
        val event = StreamingFrameReceived(
            promptExecutionId = "streaming",
            prompt = Prompt.Empty,
            model = LLModel(LLMProvider.OpenAI, "model"),
            tools = emptyList(),
            frame = frame
        )

        assertIs<PromptExecutorEvent>(event)
        assertEquals(frame, event.frame)
    }
}
