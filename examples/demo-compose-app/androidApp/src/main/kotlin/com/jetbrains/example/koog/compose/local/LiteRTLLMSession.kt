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

internal class LiteRTLLMSession(private val config: LiteRTClientConfig) {
    private var engine: Engine? = null
    private var engineConfig: EngineConfig? = null
    private var conversation: Conversation? = null
    private var conversationConfig: ConversationConfig? = null
    private val sentMessages: MutableList<Message> = mutableListOf()

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
            tools = tools.map { AndroidLocalTool(it) },
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

    fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        require(prompt.messages.isNotEmpty(), { "There should be at least one message" })
        require(tools.isEmpty(), { "Currently tools are not supported" })

        val conversation = getCurrentConversation(prompt, model, tools)

        val lastMessage = prompt.messages.last()
        val response = conversation.sendMessage(lastMessage.content)
        sentMessages.add(lastMessage)

        val responseMessages = response.toKoogMessages(config.clock)
        sentMessages.addAll(responseMessages)

        return responseMessages
    }

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
