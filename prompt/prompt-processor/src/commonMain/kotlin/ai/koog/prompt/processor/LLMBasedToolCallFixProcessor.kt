package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.jvm.JvmStatic

/**
 * A response processor that fixes incorrectly communicated tool calls.
 *
 * Applies an LLM-based approach to fix incorrectly generated tool calls.
 * Iteratively asks the LLM to update a message until it is a correct tool call.
 *
 * The first step is to identify if the corrections are needed.
 * It is done by
 *   (a) Asking the LLM if the message intends to call a tool if the message is [Message.Assistant]
 *   (b) Trying to parse the name and parameters if the message is [Message.Tool.Call]
 *
 * The main step is to fix the message (if needed).
 * The processor runs a loop asking the LLM to fix the message.
 * On every iteration, the processor provides the LLM with the current message and the feedback on it.
 * If the LLM fails to return a correct tool call message in [maxRetries] iterations, the fallback processor is used.
 * If no fallback processor is provided, the original message is returned.
 *
 * Some use-cases:
 *
 * 1. Simple usage:
 * ```kotlin
 * val processor = LLMBasedToolCallFixProcessor(toolRegistry) // Tool registry is required
 * ```
 *
 * 2. Customizing the json keys:
 *
 * ```kotlin
 * val processor = LLMBasedToolCallFixProcessor(
 *     toolRegistry,
 *     ToolCallJsonConfig(
 *         idJsonKeys = ToolCallJsonConfig.defaultIdJsonKeys + listOf("custom_id_keys", ...),
 *         nameJsonKeys = ToolCallJsonConfig.defaultNameJsonKeys + listOf("custom_name_keys", ...),
 *         argsJsonKeys = ToolCallJsonConfig.defaultArgsJsonKeys + listOf("custom_args_keys", ...),,
 *     ), // Add custom json keys produced by your LLM
 * )
 * ```
 *
 * 3. Using a fallback processor. Here the fallback processor calls another (e.g. better but more expensive) LLM to fix the message:
 * ```
 * val betterModel = OpenAIModels.Chat.GPT4o
 * val fallbackProcessor = object : ResponseProcessor() {
 *     override suspend fun process(
 *         executor: PromptExecutor,
 *         prompt: Prompt,
 *         model: LLModel,
 *         tools: List<ToolDescriptor>,
 *         responses: List<Message.Response>
 *     ): List<Message.Response> {
 *         val promptFixing = prompt(prompt) {
 *             user("please fix the following incorrectly generated tool call messages: $responses")
 *         }
 *         return executor.execute(promptFixing, betterModel, tools) // use a better LLM
 *     }
 * }
 *
 * val processor = LLMBasedToolCallFixProcessor(
 *     toolRegistry,
 *     fallbackProcessor = fallbackProcessor
 * )
 * ```
 *
 * @param toolRegistry The tool registry with available tools
 * @param toolCallJsonConfig Configuration for parsing and fixing tool call json
 * @param preprocessor A processor applied to all responses from the LLM. Defaults to [ManualToolCallFixProcessor]
 * @param assessToolCallIntentSystemMessage The system message to ask LLM if a tool call was intended
 * @param fixToolCallSystemMessage The system message to ask LLM to fix a tool call
 * @param invalidJsonFeedback The message sent to the LLM when tool call json is invalid
 * @param invalidNameFeedback The message sent to the LLM when the tool name is invalid
 * @param invalidArgumentsFeedback The message sent to the LLM when tool arguments are invalid
 * @param fallbackProcessor The fallback processor to use if LLM fails to fix a tool call.
 * Defaults to null, meaning that the original message is returned if the LLM fails to fix a tool call.
 * @param maxRetries The maximum number of iterations in the main loop
 */
public class LLMBasedToolCallFixProcessor(
    toolRegistry: ToolRegistry,
    toolCallJsonConfig: ToolCallJsonConfig = ToolCallJsonConfig(),
    private val preprocessor: ResponseProcessor = ManualToolCallFixProcessor(toolRegistry, toolCallJsonConfig),
    private val fallbackProcessor: ResponseProcessor? = null,
    private val assessToolCallIntentSystemMessage: String = Prompts.assessToolCallIntent,
    private val fixToolCallSystemMessage: String = Prompts.fixToolCall,
    private val invalidJsonFeedback: (List<ToolDescriptor>) -> String = Prompts::invalidJsonFeedback,
    private val invalidNameFeedback: (String, List<ToolDescriptor>) -> String = Prompts::invalidNameFeedback,
    private val invalidArgumentsFeedback: (String, ToolDescriptor) -> String = Prompts::invalidArgumentsFeedback,
    private val maxRetries: Int = 3,
) : ToolJsonFixProcessor(toolRegistry, toolCallJsonConfig) {

    private companion object {
        @JvmStatic
        private val logger = KotlinLogging.logger {}
    }

    init {
        require(maxRetries > 0) { "numRetries must be greater than 0" }
    }

    override suspend fun process(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>
    ): List<Message.Response> = responses.map processSingleMessage@{ response ->
        logger.info { "Updating message: $response" }

        var result = preprocessor.process(executor, prompt, model, tools, response)
        if (!isToolCallIntended(executor, prompt, model, result)) return@processSingleMessage result

        var fixToolCallPrompt = prompt(prompt.withMessages { emptyList() }) {
            system(fixToolCallSystemMessage)
        }

        var i = 0

        while (i++ < maxRetries) {
            val feedback = getFeedback(result, tools) ?: return@processSingleMessage result
            fixToolCallPrompt = prompt(fixToolCallPrompt) {
                message(result)
                user(feedback)
            }
            result = executor.executeProcessed(fixToolCallPrompt, model, tools, preprocessor).first()
        }

        // use fallback with the initial prompt
        fallbackProcessor?.process(executor, prompt, model, tools, response) ?: response
    }.also {
        logger.info { "Updated messages: $it" }
    }

    private suspend fun isToolCallIntended(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        response: Message.Response
    ): Boolean {
        if (response is Message.Tool.Call) return true

        val toolCallIntentPrompt = prompt(prompt.withMessages { emptyList() }) {
            system(assessToolCallIntentSystemMessage)
            user(response.content)
        }

        val decision = executor.execute(toolCallIntentPrompt, model, emptyList()).first()

        return decision is Message.Tool.Call ||
            decision.content.contains(
                Prompts.INTENDED_TOOL_CALL,
                ignoreCase = true
            )
    }

    private fun getFeedback(
        message: Message.Response,
        tools: List<ToolDescriptor>,
    ): String? {
        val toolName = (message as? Message.Tool.Call)?.tool
            ?: getToolName(message.content)
            ?: return invalidJsonFeedback(tools)

        if (!tools.any { it.name == toolName }) {
            return invalidNameFeedback(toolName, tools)
        }

        val tool = toolRegistry.getTool(toolName)

        try {
            tool.decodeArgs((message as Message.Tool.Call).contentJson)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            return invalidArgumentsFeedback(errorMessage, tool.descriptor)
        }

        return null
    }
}
