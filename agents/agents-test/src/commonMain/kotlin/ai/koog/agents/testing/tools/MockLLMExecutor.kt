package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.tokenizer.Tokenizer
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock

/**
 * A utility class for matching strings to associated responses based on different matching strategies.
 *
 * @param TResponse The type of the response associated with the matches.
 * @property partialMatches A map of strings to responses where the key partially matches an input string.
 * @property exactMatches A map of strings to responses where the key must match an input string exactly.
 * @property conditional A map of predicate functions to responses, where the response is determined by the first predicate that returns true for the input string.
 * @property defaultResponse The default response returned when no other match is found.
 */
internal class ResponseMatcher<TResponse>(
    val partialMatches: Map<String, TResponse>? = null,
    val exactMatches: Map<String, TResponse>? = null,
    val conditional: Map<(String) -> Boolean, TResponse>? = null,
    val defaultResponse: TResponse
)

/**
 * A mock implementation of [PromptExecutor] used for testing.
 *
 * This class simulates an LLM by returning predefined responses based on the input prompt.
 * It supports different types of matching:
 * 1. Exact matching - Returns a response when the input exactly matches a pattern
 * 2. Partial matching - Returns a response when the input contains a pattern
 * 3. Conditional matching - Returns a response when the input satisfies a condition
 * 4. Default response - Returns a default response when no other matches are found
 *
 * It also supports tool calls and can be configured to return specific tool results.
 *
 * @property handleLastAssistantMessage If true, only the last `Message.Assistant`
 *           message in a prompt is processed; otherwise, the last message of any type is used.
 * @property responseMatcher Defines the rules for matching prompts to responses,
 *           including support for exact, partial, and conditional matches as well as default responses.
 * @property moderationResponseMatcher Defines the rules for evaluating moderation
 *           matches for prompt messages.
 * @property toolRegistry Optional tool registry for tool execution
 * @property logger Logger for debugging
 * @property toolActions List of tool conditions and their corresponding actions
 * @property clock: A clock that is used for mock message timestamps
 * @property tokenizer: Tokenizer that will be used to estimate token counts in mock messages
 */
internal class MockLLMExecutor(
    private val handleLastAssistantMessage: Boolean,
    private val responseMatcher: ResponseMatcher<List<Message.Response>>,
    private val moderationResponseMatcher: ResponseMatcher<ModerationResult>,
    private val toolRegistry: ToolRegistry? = null,
    private val logger: KLogger = KotlinLogging.logger(MockLLMExecutor::class.simpleName!!),
    val toolActions: List<ToolCondition<*, *>> = emptyList(),
    private val clock: Clock = Clock.System,
    private val tokenizer: Tokenizer? = null
) : PromptExecutor {

    /**
     * Executes a prompt with tools and returns a list of responses.
     *
     * @param prompt The prompt to execute
     * @param model The LLM model to use (ignored in mock implementation)
     * @param tools The list of tools available for the execution
     * @return A list containing a single response
     */
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt with tools: ${tools.map { it.name }}" }

        return handlePrompt(prompt)
    }

    /**
     * Executes a prompt and returns a flow of string responses.
     *
     * This implementation simply wraps the result of [execute] in a flow.
     *
     * @param prompt The prompt to execute
     * @param model The LLM model to use (ignored in mock implementation)
     * @return A flow containing a single string response
     */
    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        val response = execute(prompt = prompt, model = model)
        return flowOf(response.content)
    }

    /**
     * Processes a given prompt to determine if it adheres to moderation rules and returns a moderation result.
     *
     * The method evaluates the last message in the prompt for exact and partial matches against predefined moderation rules.
     * If no matches are found, it returns a default moderation response.
     *
     * @param prompt The prompt containing the message to be moderated.
     * @param model The LLM model used for processing (ignored in this implementation).
     * @return The result of the moderation, based on matches or default rules.
     */
    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        val lastMessage = getLastMessage(prompt) ?: return moderationResponseMatcher.defaultResponse

        return findExactResponse(lastMessage, moderationResponseMatcher.exactMatches)
            ?: findPartialResponse(lastMessage, moderationResponseMatcher.exactMatches)
            ?: moderationResponseMatcher.defaultResponse
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
     * 4. Finally returning the default response if no matches are found
     *
     * @param prompt The prompt to handle
     * @return The appropriate response based on the configured matches
     */
    suspend fun handlePrompt(prompt: Prompt): List<Message.Response> {
        logger.debug { "Handling prompt with messages:" }
        prompt.messages.forEach { logger.debug { "Message content: ${it.content.take(300)}..." } }

        val inputTokensCount = tokenizer?.let { prompt.messages.map { it.content }.sumOf(it::countTokens) }

        val lastMessage = getLastMessage(prompt) ?: return responseMatcher.defaultResponse

        // Check the exact response match
        val exactMatchedResponse = findExactResponse(lastMessage, responseMatcher.exactMatches)
        if (exactMatchedResponse != null) {
            logger.debug { "Returning response for exact prompt match: $exactMatchedResponse" }
        }

        // Check partial response match
        val partiallyMatchedResponse =
            if (exactMatchedResponse == null) {
                findPartialResponse(lastMessage, responseMatcher.partialMatches)
                    ?: listOf()
            } else {
                listOf()
            }
        if (partiallyMatchedResponse.any()) {
            logger.debug { "Returning response for partial prompt match: $partiallyMatchedResponse" }
        }

        // Check request conditions
        val conditionals = getConditionalResponse(lastMessage, inputTokensCount) ?: listOf()

        val result = (exactMatchedResponse ?: listOf()) + partiallyMatchedResponse + conditionals
        if (result.any()) {
            return result
        }

        // Process the default LLM response
        return responseMatcher.defaultResponse
    }

    private fun getConditionalResponse(
        lastMessage: Message,
        inputTokensCount: Int?
    ): List<Message.Response>? = if (!responseMatcher.conditional.isNullOrEmpty()) {
        responseMatcher.conditional.entries.firstOrNull { it.key(lastMessage.content) }?.let { (_, response) ->
            logger.debug { "Returning response for conditional match: $response" }
            response
        }
    } else {
        emptyList()
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
        partialMatches: Map<String, TResponse>?
    ): TResponse? {
        return partialMatches?.entries?.firstNotNullOfOrNull { (pattern, response) ->
            if (message.content.contains(pattern)) {
                response
            } else {
                null
            }
        }
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
        exactMatches: Map<String, TResponse>?
    ): TResponse? {
        return exactMatches?.entries?.firstNotNullOfOrNull { (pattern, response) ->
            if (message.content == pattern) {
                response
            } else {
                null
            }
        }
    }
}
