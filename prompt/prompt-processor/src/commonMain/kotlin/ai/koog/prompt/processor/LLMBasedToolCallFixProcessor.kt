package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import kotlin.jvm.JvmOverloads

/**
 * A response processor that fixes incorrectly communicated tool calls.
 *
 * Applies an LLM-based approach to fix incorrectly generated tool calls.
 * Iteratively asks the LLM to update a message until it is a correct tool call.
 *
 * The first step is to identify if the corrections are needed.
 * It is done by
 *   (a) Asking the LLM if the message intends to call a tool if the message is [Message.Assistant]
 *   (b) Trying to parse the name and parameters if the message is [MessagePart.Tool.Call]
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
 * Defaults to null, meaning that the original message is returned if the LLM fails to fix a tool call.
 * @param maxRetries The maximum number of iterations in the main loop
 */
public class LLMBasedToolCallFixProcessor @JvmOverloads constructor(
    toolRegistry: ToolRegistry,
    toolCallJsonConfig: ToolCallJsonConfig = ToolCallJsonConfig(),
    private val preprocessor: ResponseProcessor = ManualToolCallFixProcessor(toolRegistry, toolCallJsonConfig),
    private val assessToolCallIntentSystemMessage: String = Prompts.assessToolCallIntent,
    private val fixToolCallSystemMessage: String = Prompts.fixToolCall,
    private val invalidJsonFeedback: (List<ToolDescriptor>) -> String = Prompts::invalidJsonFeedback,
    private val invalidNameFeedback: (String, List<ToolDescriptor>) -> String = Prompts::invalidNameFeedback,
    private val invalidArgumentsFeedback: (String, ToolDescriptor) -> String = Prompts::invalidArgumentsFeedback,
    private val maxRetries: Int = 3,
) : ToolJsonFixProcessor(toolRegistry, toolCallJsonConfig) {

    init {
        require(maxRetries > 0) { "numRetries must be greater than 0" }
    }

    override suspend fun process(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        response: Message.Assistant,
        serializer: JSONSerializer,
    ): Message.Assistant {
        val response = preprocessor.process(executor, prompt, model, tools, response, serializer)
        return Message.Assistant(
            parts = response.parts.map { part ->
                processMessagePart(executor, prompt, model, tools, part, serializer)
            },
            finishReason = response.finishReason,
            metaInfo = response.metaInfo
        )
    }

    private suspend fun processMessagePart(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        messagePart: MessagePart.ResponsePart,
        serializer: JSONSerializer,
    ): MessagePart.ResponsePart {
        if (!isToolCallRequired(prompt.params.toolChoice) &&
            !isToolCallIntended(executor, prompt, model, messagePart)
        ) {
            return messagePart
        }

        var i = 0
        var toolPart = messagePart

        while (i++ < maxRetries) {
            val feedback = getFeedback(toolPart, tools, serializer) ?: return toolPart
            val fixToolCallPrompt = prompt(prompt.withMessages { emptyList() }) {
                system(fixToolCallSystemMessage)
                assistant {
                    when (toolPart) {
                        is MessagePart.Text -> text((toolPart as MessagePart.Text).text)
                        is MessagePart.Tool.Call -> toolCall(toolPart as MessagePart.Tool.Call)
                        else -> {}
                    }
                }
                user(feedback)
            }

            val processedResponse = executor.executeProcessed(
                prompt = fixToolCallPrompt,
                model = model,
                tools = tools,
                processorConfig = ResponseProcessorConfig(preprocessor, serializer)
            )

            processedResponse.parts.firstOrNull { it is MessagePart.Tool.Call }?.let { return it }

            toolPart = processedResponse.parts.firstOrNull { it is MessagePart.Text } as? MessagePart.Text
                ?: return toolPart
        }

        return toolPart
    }

    private fun isToolCallRequired(toolChoice: LLMParams.ToolChoice?) = when (toolChoice) {
        null -> false
        LLMParams.ToolChoice.Named -> true
        LLMParams.ToolChoice.None -> false
        LLMParams.ToolChoice.Auto -> false
        LLMParams.ToolChoice.Required -> true
        else -> error("Unknown tool choice: $toolChoice")
    }

    private suspend fun isToolCallIntended(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        response: MessagePart.ResponsePart
    ): Boolean {
        when (response) {
            is MessagePart.Text -> {
                val toolCallIntentPrompt = prompt(prompt.withMessages { emptyList() }) {
                    system(assessToolCallIntentSystemMessage)
                    user(response.text)
                }
                val decision = executor.execute(toolCallIntentPrompt, model, emptyList())
                return decision.parts.any {
                    it is MessagePart.Tool.Call ||
                        (it is MessagePart.Text && it.text.contains(Prompts.INTENDED_TOOL_CALL, ignoreCase = true))
                }
            }

            else -> return true
        }
    }

    private fun getFeedback(
        messagePart: MessagePart.ResponsePart,
        tools: List<ToolDescriptor>,
        serializer: JSONSerializer,
    ): String? {
        val toolName = (messagePart as? MessagePart.Tool.Call)?.tool
            ?: (messagePart as? MessagePart.Text)?.let { getToolName(it.text) }
            ?: return invalidJsonFeedback(tools)

        if (!tools.any { it.name == toolName }) {
            return invalidNameFeedback(toolName, tools)
        }

        val tool = try {
            toolRegistry.getTool(toolName)
        } catch (_: Exception) {
            // assume that it's the hack tool from the subgraphWithTask, since it is available in `tools`, but not available in the `toolRegistry`
            return null
        }
        try {
            tool.decodeArgs((messagePart as MessagePart.Tool.Call).argsJson.toKoogJSONObject(), serializer)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            return invalidArgumentsFeedback(errorMessage, tool.descriptor)
        }

        return null
    }
}
