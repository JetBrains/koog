package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
 */
internal class MockLLMExecutor(
    private val partialMatches: Map<String, Message.Response>? = null,
    private val exactMatches: Map<String, Message.Response>? = null,
    private val conditional: Map<(String) -> Boolean, String>? = null,
    private val defaultResponse: String = "",
    private val toolRegistry: ToolRegistry? = null,
    private val logger: MPPLogger = LoggerFactory.create(MockLLMExecutor::class.simpleName!!),
    val toolActions: List<ToolCondition<*, *>> = emptyList()
) : PromptExecutor {
    /**
     * Executes a prompt without tools and returns a string response.
     *
     * @param prompt The prompt to execute
     * @param model The LLM model to use (ignored in mock implementation)
     * @return The content of the response as a string
     */
    override suspend fun execute(prompt: Prompt, model: LLModel): String {
        logger.debug { "Executing prompt without tools, prompt: $prompt" }
        return handlePrompt(prompt).content
    }

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
        return flowOf(execute(prompt, model))
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

        val lastMessage = prompt.messages.lastOrNull() ?: return Message.Assistant(defaultResponse)

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
                return Message.Assistant(response)
            }
        }

        // Process the default LLM response
        return Message.Assistant(defaultResponse)
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
