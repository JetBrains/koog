package ai.koog.agents.testing.tools.builder

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.testing.tools.MockExecutorBuilder
import ai.koog.agents.testing.tools.ToolCondition
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorBuilder
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.prompt.tokenizer.Tokenizer
import ai.koog.serialization.JSONSerializer
import ai.koog.utils.time.KoogClock
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.jvm.JvmStatic

/**
 * A utility class for matching strings to associated responses based on different matching strategies.
 *
 * @property partialMatches A map of strings to responses where the key partially matches an input string.
 * @property exactMatches A map of strings to responses where the key must match an input string exactly.
 * @property conditional A map of predicate functions to responses, where the response is determined
 *  by the first predicate that returns true for the input string.
 * @property defaultResponse The default response returned when no other match is found.
 */
internal class ResponseMatcher<TResponse>(
    val partialMatches: Map<String, TResponse>? = null,
    val exactMatches: Map<String, TResponse>? = null,
    val conditional: Map<(String) -> Boolean, TResponse>? = null,
    val defaultResponse: TResponse,
)

/**
 * Builder for a mock [PromptExecutor] used in tests. See [MockExecutorBuilder] (fluent DSL) for
 * the canonical construction path.
 *
 * @property handleLastAssistantMessage If true, only the last `Message.Assistant`
 *           message in a prompt is processed; otherwise, the last message of any type is used.
 * @property responseMatcher Defines the rules for matching prompts to responses.
 * @property moderationResponseMatcher Defines the rules for evaluating moderation matches.
 * @property logger Logger for debugging.
 * @property toolActions List of tool conditions and their corresponding actions.
 * @property clock A clock used for mock message timestamps.
 * @property tokenizer Tokenizer that estimates token counts in mock messages.
 */
public class MockPromptExecutorBuilder internal constructor(
    private val handleLastAssistantMessage: Boolean,
    private val responseMatcher: ResponseMatcher<Message.Assistant>,
    private val moderationResponseMatcher: ResponseMatcher<ModerationResult>,
    private val streamResponseMatcher: ResponseMatcher<Flow<StreamFrame>>,
    private val logger: KLogger = KotlinLogging.logger(MockPromptExecutorBuilder::class.simpleName.toString()),
    internal val toolActions: List<ToolCondition<*, *>> = emptyList(),
    private val clock: KoogClock = KoogClock.System,
    private val tokenizer: Tokenizer? = null,
) : PromptExecutorBuilder() {

    public companion object {
        @JvmStatic
        @JavaAPI
        public fun builder(serializer: JSONSerializer): MockExecutorBuilder = MockExecutorBuilder(serializer)
    }

    override suspend fun onExecute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        logger.debug { "Executing prompt with tools: ${tools.map { it.name }}" }
        return handlePrompt(prompt)
    }

    override fun onStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        val lastMessage = getLastMessage(prompt)
        val matchedStream = lastMessage?.let {
            findExactResponse(it, streamResponseMatcher.exactMatches)
                ?: findPartialResponse(it, streamResponseMatcher.partialMatches)
        }

        return matchedStream ?: flow {
            handlePrompt(prompt).toStreamFrames().forEach { emit(it) }
        }
    }

    override suspend fun onModerate(prompt: Prompt, model: LLModel): ModerationResult {
        val lastMessage = getLastMessage(prompt) ?: return moderationResponseMatcher.defaultResponse

        return findExactResponse(lastMessage, moderationResponseMatcher.exactMatches)
            ?: findPartialResponse(lastMessage, moderationResponseMatcher.exactMatches)
            ?: moderationResponseMatcher.defaultResponse
    }

    private val Message.textContent: String
        get() {
            val textParts = parts.filterIsInstance<MessagePart.Text>().map { it.text }
            val toolResults = parts.filterIsInstance<MessagePart.Tool.Result>().map { it.output }
            return (textParts + toolResults).joinToString("\n")
        }

    private fun getLastMessage(prompt: Prompt): Message? {
        return if (handleLastAssistantMessage && prompt.messages.any { it is Message.Assistant }) {
            prompt.messages.lastOrNull { it is Message.Assistant }
        } else {
            prompt.messages.lastOrNull()
        }
    }

    /**
     * Handles a prompt and returns an appropriate response based on the configured matches.
     *
     * This method processes the prompt by:
     * 1. First checking for exact matches
     * 2. Then checking for partial matches
     * 3. Then checking for conditional matches
     * 4. Finally, returning the default response if no matches are found
     *
     * @param prompt The prompt to handle
     * @return The appropriate response based on the configured matches
     */
    private fun handlePrompt(prompt: Prompt): Message.Assistant {
        logger.debug { "Handling prompt with messages:" }
        prompt.messages.forEach { logger.debug { "Message content: ${it.textContent.take(300)}..." } }

        val lastMessage = getLastMessage(prompt) ?: return responseMatcher.defaultResponse
        val lastMessageText = lastMessage.textContent

        // Check exact match first
        val exactMatchedResponse = findExactResponse(lastMessage, responseMatcher.exactMatches)
        if (exactMatchedResponse != null) {
            logger.debug { "Returning response for exact prompt match: $exactMatchedResponse" }
            return updateTokenCounts(exactMatchedResponse, lastMessageText)
        }

        // Check partial match
        val partiallyMatchedResponse = findPartialResponse(lastMessage, responseMatcher.partialMatches)
        if (partiallyMatchedResponse != null) {
            logger.debug { "Returning response for partial prompt match: $partiallyMatchedResponse" }
            return updateTokenCounts(partiallyMatchedResponse, lastMessageText)
        }

        // Check conditional match
        val conditionalResponse = getConditionalResponse(lastMessage)
        if (conditionalResponse != null) {
            return updateTokenCounts(conditionalResponse, lastMessageText)
        }

        // Fall back to default response
        return updateTokenCounts(responseMatcher.defaultResponse, lastMessageText)
    }

    private fun getConditionalResponse(lastMessage: Message): Message.Assistant? =
        responseMatcher.conditional
            ?.entries
            ?.firstOrNull { it.key(lastMessage.textContent) }
            ?.let { (_, response) ->
                logger.debug { "Returning response for conditional match: $response" }
                response
            }

    /**
     * Updates the token counts in response metadata to use the input string.
     */
    private fun updateTokenCounts(
        response: Message.Assistant,
        input: String,
    ): Message.Assistant {
        if (tokenizer == null) return response

        val inputTokenCount = tokenizer.countTokens(input)

        val outputContent = response.parts.joinToString("") { part ->
            when (part) {
                is MessagePart.Text -> part.text
                is MessagePart.Tool.Call -> part.args
                is MessagePart.Reasoning -> part.content.joinToString()
                else -> ""
            }
        }
        val outputTokenCount = tokenizer.countTokens(outputContent)
        val updatedMetaInfo = ResponseMetaInfo.create(
            clock = clock,
            inputTokensCount = inputTokenCount,
            outputTokensCount = outputTokenCount,
            totalTokensCount = inputTokenCount + outputTokenCount,
        )
        return response.copy(metaInfo = updatedMetaInfo)
    }

    /*
    Additional helper functions
     */

    /**
     * Finds a response that matches the message content partially.
     *
     * @param message The message to check
     * @param partialMatches Map of patterns to responses for partial matching
     * @return The matching response, or null if no match is found
     */
    private fun <TResponse> findPartialResponse(
        message: Message,
        partialMatches: Map<String, TResponse>?,
    ): TResponse? = partialMatches?.entries?.firstNotNullOfOrNull { (pattern, response) ->
        if (message.textContent.contains(pattern)) response else null
    }

    /**
     * Finds a response that matches the message content exactly.
     *
     * @param message The message to check
     * @param exactMatches Map of patterns to responses for exact matching
     * @return The matching response, or null if no match is found
     */
    private fun <TResponse> findExactResponse(
        message: Message,
        exactMatches: Map<String, TResponse>?,
    ): TResponse? = exactMatches?.entries?.firstNotNullOfOrNull { (pattern, response) ->
        if (message.textContent == pattern) response else null
    }
}
