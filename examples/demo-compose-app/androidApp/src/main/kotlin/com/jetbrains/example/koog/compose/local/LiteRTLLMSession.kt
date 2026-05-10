package com.jetbrains.example.koog.compose.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool

/**
 * Manages a stateful LiteRT inference session for a single [LiteRTLLMClient].
 *
 * Maintains a lazily created [Engine] and [Conversation], reusing them across
 * successive [execute] calls when the model and message history have not changed.
 * The engine is recreated when a different model is requested, and the conversation
 * is recreated when the prompt history diverges from the cached history.
 *
 * @param config Configuration for the LiteRT client, including model paths and backend.
 */
internal class LiteRTLLMSession(private val config: LiteRTClientConfig) {
    private var engine: Engine? = null
    private var engineConfig: EngineConfig? = null
    private var conversation: Conversation? = null
    private var conversationConfig: ConversationConfig? = null
    private val sentMessages: MutableList<Message> = mutableListOf()

    /**
     * Returns the current [Engine] if it already targets [model], otherwise
     * closes the existing engine and initializes a new one for the given model.
     *
     * @param model The LLM whose file path is used to configure the engine.
     * @return An initialized [Engine] ready to create conversations.
     */
    private fun getCurrentEngine(model: LLModel): Engine {
        val modelPath = "${config.modelsPath}/${model.id}"
        if (engine != null && engineConfig?.modelPath == modelPath) {
            return engine!!
        }

        val newEngineConfig = EngineConfig(
            modelPath = "${config.modelsPath}/${model.id}",
            backend = config.backend,
        )

        val newEngine = Engine(newEngineConfig)

        close()
        engineConfig = newEngineConfig
        engine = newEngine

        newEngine.initialize()

        return engine!!
    }

    /**
     * Returns an existing [Conversation] if its message prefix matches the current prompt,
     * otherwise closes the old conversation and creates a new one configured with the
     * given sampling parameters and tools.
     *
     * @param prompt The full prompt whose messages (except the last) form the conversation prefix.
     * @param model The LLM to use; triggers engine reinitialization if changed.
     * @param tools Tool descriptors to register with the conversation.
     * @return A [Conversation] whose history matches the prompt prefix.
     */
    private fun getCurrentConversation(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Conversation {
        val currentEngine = getCurrentEngine(model)

        val messagePrefix = prompt.messages.dropLast(1)

        if (conversation != null) {
            if (sentMessages == messagePrefix) {
                return conversation!!
            }
            // TODO: check tools & samplerConfig
        }

        val params: AndroidLocalLLMParams = prompt.params.toAndroidLocalParams()

        val newConversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(
                topK = params.topK,
                topP = params.topP,
                temperature = params.exactTemperature,
            ),
            initialMessages = messagePrefix.map { it.toLitertMessage() },
            tools = tools.map { tool(AndroidLocalTool(it)) },
            automaticToolCalling = false
        )

        conversation?.close()
        sentMessages.clear()

        val newConversation = currentEngine.createConversation(newConversationConfig)
        conversationConfig = newConversationConfig
        conversation = newConversation
        sentMessages.addAll(messagePrefix)

        return newConversation
    }

    /**
     * Sends the last message of [prompt] to the model and returns the response.
     *
     * Reuses the cached [Conversation] when the prompt history prefix is unchanged.
     * The response messages are appended to the internal sent-message history so
     * subsequent calls can detect history continuity.
     *
     * @param prompt Prompt containing at least one message; all but the last are used
     *   as conversation context and the last is sent as the new user turn.
     * @param model The LLM to run inference with.
     * @param tools Tool descriptors; currently must be empty.
     * @return List of [Message.Response] objects produced by the model.
     * @throws IllegalArgumentException if [prompt] contains no messages or [tools] is non-empty.
     */
    fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        require(prompt.messages.isNotEmpty(), { "There should be at least one message" })

        val conversation = getCurrentConversation(prompt, model, tools)

        val lastMessage = prompt.messages.last()
        val response = conversation.sendMessage(lastMessage.content)
        sentMessages.add(lastMessage)

        val responseMessages = response.toKoogMessages(config.clock)
        sentMessages.addAll(responseMessages)

        return responseMessages
    }

    /**
     * Closes and releases all LiteRT resources held by this session.
     *
     * Closes the active [Conversation] and [Engine], resets all cached state,
     * and clears the sent-message history. Safe to call multiple times.
     */
    fun close() {
        conversation?.close()
        engine?.close()
        engine = null
        engineConfig = null
        conversation = null
        conversationConfig = null
        sentMessages.clear()
    }
}
