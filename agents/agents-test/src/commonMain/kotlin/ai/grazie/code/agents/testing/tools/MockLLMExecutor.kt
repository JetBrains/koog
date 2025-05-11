package ai.grazie.code.agents.testing.tools

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class MockLLMExecutor(
    private val partialMatches: Map<String, Message.Response>? = null,
    private val exactMatches: Map<String, Message.Response>? = null,
    private val conditional: Map<(String) -> Boolean, String>? = null,
    private val defaultResponse: String = "",
    private val toolRegistry: ToolRegistry? = null,
    private val logger: MPPLogger = LoggerFactory.create(MockLLMExecutor::class.simpleName!!),
    val toolActions: List<ToolCondition<*, *>> = emptyList()
) : PromptExecutor {
    // Executing just prompt
    override suspend fun execute(prompt: Prompt, model: LLModel): String {
        logger.debug { "Executing prompt without tools, prompt: $prompt" }
        return handlePrompt(prompt).content
    }

    // Executing a tool call
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt with tools: ${tools.map { it.name }}" }

        val response = handlePrompt(prompt)
        return listOf(response)
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        return flowOf(execute(prompt, model))
    }

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

    // Checking if the user's message partly corresponds to the given pattern
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

    // Checking if the user's message fully equals the given pattern
    private fun findExactResponse(message: Message, exactMatches: Map<String, Message.Response>?): Message.Response? {
        return exactMatches?.entries?.firstNotNullOfOrNull { (pattern, response) ->
            if (message.content == pattern) {
                response
            } else null
        }
    }
}
