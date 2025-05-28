package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.tokenizer.Tokenizer
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock

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
 * @property partialMatches Map of patterns to responses for partial matching
 * @property exactMatches Map of patterns to responses for exact matching
 * @property conditional Map of conditions to responses for conditional matching
 * @property defaultResponse Default response to return when no other matches are found
 * @property toolRegistry Optional tool registry for tool execution
 * @property logger Logger for debugging
 * @property toolActions List of tool conditions and their corresponding actions
 * @property clock: A clock that is used for mock message timestamps
 * @property tokenizer: Tokenizer that will be used to estimate token counts in mock messages
 */
internal class MockLLMExecutor(
    private val partialMatches: Map<String, Message.Response>? = null,
    private val exactMatches: Map<String, Message.Response>? = null,
    private val conditional: Map<(String) -> Boolean, String>? = null,
    private val defaultResponse: String = "",
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

        val response = handlePrompt(prompt)
        return listOf(response)
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
    suspend fun handlePrompt(prompt: Prompt): Message.Response {
        logger.debug { "Handling prompt with messages:" }
        prompt.messages.forEach { logger.debug { "Message content: ${it.content.take(300)}..." } }

        val inputTokensCount = tokenizer?.let { prompt.messages.map { it.content }.sumOf(it::countTokens) }

        val lastMessage = prompt.messages.lastOrNull() ?: return Message.Assistant(
            defaultResponse,
            metaInfo = ResponseMetaInfo.create(
                clock,
                totalTokensCount = tokenizer?.countTokens(defaultResponse)?.let { it + inputTokensCount!! },
                inputTokensCount = inputTokensCount,
                outputTokensCount = tokenizer?.countTokens(defaultResponse),
            )
        )

        // Check the exact response match
        val exactMatchedResponse = findExactResponse(lastMessage, exactMatches)
        if (exactMatchedResponse != null) {
            logger.debug { "Returning response for exact prompt match: $exactMatchedResponse" }

            // Check if LLM messages contain any of the patterns and call the corresponding tool if they do
            return exactMatchedResponse
        }

        // Check partial response match
        val partiallyMatchedResponse = findPartialResponse(lastMessage, partialMatches)
        if (partiallyMatchedResponse != null) {
            logger.debug { "Returning response for partial prompt match: $partiallyMatchedResponse" }

            // Check if LLM messages contain any of the patterns and call the corresponding tool if they do
            return partiallyMatchedResponse
        }

        // Check request conditions
        if (!conditional.isNullOrEmpty()) {
            conditional.entries.firstOrNull { it.key(lastMessage.content) }?.let { (_, response) ->
                logger.debug { "Returning response for conditional match: $response" }

                // Check if LLM messages contain any of the patterns and call the corresponding tool if they do
                return Message.Assistant(
                    response,
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = tokenizer?.countTokens(response)?.let { it + inputTokensCount!! },
                        inputTokensCount = inputTokensCount,
                        outputTokensCount = tokenizer?.countTokens(response)
                    )
                )
            }
        }

        // Process the default LLM response
        return Message.Assistant(
            defaultResponse,
            metaInfo = ResponseMetaInfo.create(
                clock,
                totalTokensCount = tokenizer?.countTokens(defaultResponse)?.let { it + inputTokensCount!! },
                inputTokensCount = inputTokensCount,
                outputTokensCount = tokenizer?.countTokens(defaultResponse),
            )
        )
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
    private fun findPartialResponse(
        message: Message,
        partialMatches: Map<String, Message.Response>?
    ): Message.Response? {
        return partialMatches?.entries?.firstNotNullOfOrNull { (pattern, response) ->
            if (message.content.contains(pattern)) {
                response
            } else null
        }
    }

    /**
     * Finds a response that matches the message content exactly.
     *
     * @param message The message to check
     * @param exactMatches Map of patterns to responses for exact matching
     * @return The matching response, or null if no match is found
     */
    private fun findExactResponse(message: Message, exactMatches: Map<String, Message.Response>?): Message.Response? {
        return exactMatches?.entries?.firstNotNullOfOrNull { (pattern, response) ->
            if (message.content == pattern) {
                response
            } else null
        }
    }
}
