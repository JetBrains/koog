package ai.koog.agents.testing.tools.factory

import ai.koog.agents.testing.tools.ToolCondition
import ai.koog.agents.testing.tools.builder.MockPromptExecutorBuilder
import ai.koog.agents.testing.tools.builder.ResponseMatcher
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.tokenizer.Tokenizer
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.flow.Flow

/**
 * Source-compatible factory for [MockPromptExecutorBuilder]. Returns a built [PromptExecutor].
 */
@Suppress("FunctionName")
internal fun MockPromptExecutor(
    handleLastAssistantMessage: Boolean,
    responseMatcher: ResponseMatcher<Message.Assistant>,
    moderationResponseMatcher: ResponseMatcher<ModerationResult>,
    streamResponseMatcher: ResponseMatcher<Flow<StreamFrame>>,
    toolActions: List<ToolCondition<*, *>> = emptyList(),
    clock: KoogClock = KoogClock.System,
    tokenizer: Tokenizer? = null,
): PromptExecutor = MockPromptExecutorBuilder(
    handleLastAssistantMessage = handleLastAssistantMessage,
    responseMatcher = responseMatcher,
    moderationResponseMatcher = moderationResponseMatcher,
    streamResponseMatcher = streamResponseMatcher,
    toolActions = toolActions,
    clock = clock,
    tokenizer = tokenizer,
).build()
