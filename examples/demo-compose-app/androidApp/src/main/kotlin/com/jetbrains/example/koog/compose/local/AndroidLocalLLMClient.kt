package com.jetbrains.example.koog.compose.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import kotlinx.datetime.Clock
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.ExperimentalTime

data class AndroidLocalLLMClientConfig(
    val defaultModel: LLModel = FunctionGemma,
    val modelsPath: String = "/data/local/tmp/llm",
    val cacheDir: String = "/data/local/tmp/llm/cache",
    val backend: Backend = Backend.CPU(),
    @OptIn(ExperimentalTime::class)
    val clock: Clock = Clock.System,
)

class AndroidLocalLLMClient(private val config: AndroidLocalLLMClientConfig) : LLMClient {

    private var engine: Engine? = null

    private var conversation: Conversation? = null

    private val sentMessages: MutableList<Message> = mutableListOf()
    private val usedTools: MutableList<ToolDescriptor> = mutableListOf()

    private fun appendMessages(messages: List<Message>) {
        sentMessages.addAll(messages)
    }

    private fun appendMessage(message: Message) {

    }

    private fun findUndentMessages(messages: List<Message>): List<Message> {
        return messages.filter { !sentMessages.contains(it) }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        val engineConfig = EngineConfig(
            modelPath = "${config.modelsPath}/${model.id}",
            backend = config.backend,
        )

        engine = Engine(engineConfig)
        engine?.initialize()

        val conversationConfig = ConversationConfig(
            // TODO: set from prompt params
            samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
            // TODO: set from prompt messages
//            initialMessages =
            // Convert from tools
            tools = listOf(tool(SampleOpenApiTool())),
            automaticToolCalling = false
        )

        conversation = engine?.createConversation(conversationConfig)

        val response = conversation?.sendMessage(prompt.messages.last().content)

        conversation?.close()
        engine?.close()

        return response?.toKoogMessages(config.clock) ?: emptyList()
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not supported for Android local models")
    }

    override fun llmProvider(): LLMProvider = AndroidLocalLLMProvider

    @OptIn(ExperimentalAtomicApi::class)
    override fun close() {
        conversation?.close()
        engine?.close()
    }
}

class SampleOpenApiTool : OpenApiTool {

    override fun getToolDescriptionJsonString(): String {
        return """
            {
              "name": "get_weather",
              "description": "Returns the weather for a given location.",
              "parameters": {
                "type": "object",
                "properties": {
                  "location": {
                    "type": "string",
                    "description": "The location to get the weather for."
                  }
                },
                "required": [
                  "location"
                ]
              }
            }
        """
    }

    override fun execute(paramsJsonString: String): String {
        // Parse paramsJsonString with your choice of parser/deserializer and
        // execute the tool.

        // Return the result as a JSON string
        return "Weather for $paramsJsonString is sunny, temperature is 25 degrees."
    }
}


class SampleToolSet : ToolSet {
    @Tool(description = "Get weather for a location.")
    fun getWeather(
        @ToolParam(description = "The location.") location: String,
    ): String {
        return "Weather for $location is sunny, temperature is 25 degrees."
    }
}
