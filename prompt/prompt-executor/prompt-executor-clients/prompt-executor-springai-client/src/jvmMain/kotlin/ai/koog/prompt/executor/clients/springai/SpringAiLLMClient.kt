package ai.koog.prompt.executor.clients.springai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions

/**
 * A client implementation for interacting with an AI language model via a chat-based API.
 * Provides execution capabilities for handling prompts and generating responses from the language model.
 *
 * This class is designed to integrate with a `ChatClient` for handling requests and responses
 * to and from the underlying large language model.
 *
 * Implements the `LLMClient` interface, offering both standard and streaming-based execution methods.
 *
 * @param chatClient The chat client used to facilitate communication with the LLM service.
 */
public class SpringAiLLMClient(
    private val chatClient: ChatClient
) : LLMClient {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        val chatResponse = prepareClientRequest(prompt, model)
            .call()
            .chatResponse()!!
        val text = chatResponse.result.output.text!!
        return listOf(
            Message.Assistant(
                content = text,
                finishReason = chatResponse.result.metadata.finishReason
            )
        )
    }

    override suspend fun executeStreaming(
        prompt: Prompt,
        model: LLModel
    ): Flow<String> {
        return prepareClientRequest(prompt, model)
            .stream()
            .chatResponse()
            .asFlow()
            .map { it.result?.output?.text }
            .filterNotNull()
    }

    private fun prepareClientRequest(
        prompt: Prompt,
        model: LLModel,
    ): ChatClient.ChatClientRequestSpec {
        val springAiMessages = prompt.messages.map {
            when (it) {
                is Message.System -> {
                    SystemMessage(it.content)
                }

                is Message.User -> {
                    UserMessage(it.content)
                }

                is Message.Assistant -> {
                    AssistantMessage(it.content)
                }

                is Message.Tool.Call -> {
                    AssistantMessage(
                        "",
                        emptyMap(),
                        listOf(
                            AssistantMessage.ToolCall(
                                requireNotNull(it.id) { "Tool id is required"},
                                "function",
                                it.tool,
                                it.content
                            )
                        )
                    )
                }

                is Message.Tool.Result -> {
                    ToolResponseMessage(
                        listOf(
                            ToolResponseMessage.ToolResponse(
                                requireNotNull(it.id) { "Tool id is required"},
                                it.tool,
                                it.content
                            )
                        )
                    )
                }
            }
        }

        return chatClient
            .prompt()
            .messages(springAiMessages)
            .options(
                ChatOptions
                    .builder()
                    .model(model.id)
                    .build(),
            )
    }
}
