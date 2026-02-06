package ai.koog.integration.tests.utils

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentBuilder
import ai.koog.agents.core.agent.FunctionalAgentBuilder
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.function.Function

/**
 *
 * The object is necessary to bridge the gap between Kotlin coroutines/DSL and Java.
 * It's necessary because Java doesn't natively support some used Kotlin concepts like runBlocking.
 */

object JavaInteropUtils {
    @JvmStatic
    fun createOpenAIClient(apiKey: String): OpenAILLMClient = OpenAILLMClient(apiKey)

    @JvmStatic
    fun createAnthropicClient(apiKey: String): AnthropicLLMClient = AnthropicLLMClient(apiKey)

    @JvmStatic
    fun buildSimplePrompt(id: String, system: String, user: String): Prompt {
        return Prompt.builder(id)
            .system(system)
            .user(user)
            .build()
    }

    @JvmStatic
    fun executeClientBlocking(
        client: LLMClient,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response> = runBlocking {
        client.execute(prompt, model, tools)
    }

    @JvmStatic
    fun executeExecutorBlocking(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response> = runBlocking {
        executor.execute(prompt, model, tools)
    }

    @JvmStatic
    fun createMultiLLMPromptExecutor(
        openAIClient: OpenAILLMClient,
        anthropicClient: AnthropicLLMClient
    ): MultiLLMPromptExecutor {
        return MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient
        )
    }

    @JvmStatic
    fun getAssistantContent(message: Message.Assistant): String = message.content

    @JvmStatic
    fun getToolName(call: Message.Tool.Call): String = call.tool

    @JvmStatic
    fun runAgentBlocking(agent: AIAgent<String, String>, input: String): String = runBlocking {
        agent.run(input)
    }

    @JvmStatic
    fun requestLLM(
        context: AIAgentFunctionalContext,
        input: String,
        includeHistory: Boolean = true
    ): Message.Response = context.requestLLM(input, includeHistory, null)

    @JvmStatic
    fun sendToolResult(
        context: AIAgentFunctionalContext,
        toolResult: ReceivedToolResult
    ): Message.Response = context.sendToolResult(toolResult, null)

    @JvmStatic
    fun executeTool(
        context: AIAgentFunctionalContext,
        toolCall: Message.Tool.Call
    ): ReceivedToolResult = context.executeTool(toolCall, null)

    @JvmStatic
    fun createAgentBuilder(): AIAgentBuilder = AIAgent.builder()

    @JvmStatic
    fun buildFunctionalAgent(builder: FunctionalAgentBuilder<String, String>): AIAgent<String, String> = builder.build()

    @JvmStatic
    fun <I, O : Any> runSubtask(
        context: AIAgentFunctionalContext,
        description: String,
        input: I,
        outputType: Class<O>,
        tools: List<Tool<*, *>>?,
        model: LLModel
    ): O = context.subtask(description)
        .withInput(input)
        .withOutput(outputType)
        .apply { tools?.let { withTools(it) } }
        .useLLM(model)
        .run()

    @JvmStatic
    fun <T : Any> requestLLMStructuredBlocking(
        context: AIAgentFunctionalContext,
        message: String,
        outputType: Class<T>
    ): T = runBlocking {
        context.requestLLMStructured(message, outputType.kotlin, emptyList(), null).getOrThrow().data
    }

    @JvmStatic
    fun <T> llmWriteSession(
        context: AIAgentFunctionalContext,
        action: Function<AIAgentLLMWriteSession, T>
    ): T = context.llm.writeSession(action)

    @JvmStatic
    fun <T> llmReadSession(
        context: AIAgentFunctionalContext,
        action: Function<AIAgentLLMReadSession, T>
    ): T = context.llm.readSession(action)

    @JvmStatic
    fun executeMultipleTools(
        context: AIAgentFunctionalContext,
        toolCalls: List<Message.Tool.Call>,
        parallel: Boolean
    ): List<ReceivedToolResult> = runBlocking {
        context.executeMultipleTools(toolCalls, parallel)
    }

    @JvmStatic
    fun sendMultipleToolResults(
        context: AIAgentFunctionalContext,
        results: List<ReceivedToolResult>
    ): List<Message.Response> = runBlocking {
        context.sendMultipleToolResults(results)
    }

    @JvmStatic
    fun getHistory(context: AIAgentFunctionalContext): List<Message> = try {
        runBlocking { context.getHistory() }
    } catch (e: Exception) {
        throw RuntimeException("Failed to retrieve history", e)
    }

    @JvmStatic
    fun createToolRegistry(toolSet: ToolSet): ToolRegistry {
        return ToolRegistry.builder()
            .tools(toolSet, Json.Default)
            .build()
    }

    class CalculatorTools : ToolSet {
        private val toolRegistry by lazy { createToolRegistry(this) }

        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription(description = "Adds two numbers together")
        fun add(
            @LLMDescription(description = "First number") a: Int,
            @LLMDescription(description = "Second number") b: Int
        ): Int = a + b

        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription(description = "Multiplies two numbers")
        fun multiply(
            @LLMDescription(description = "First number") a: Int,
            @LLMDescription(description = "Second number") b: Int
        ): Int = a * b

        fun getAddTool(): Tool<*, *> =
            toolRegistry.tools.first { it.name == "add" }

        fun getMultiplyTool(): Tool<*, *> =
            toolRegistry.tools.first { it.name == "multiply" }
    }

    class TransactionTools : ToolSet {
        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription(description = "Gets the transaction ID for a given order number. You must call this tool to retrieve transaction IDs.")
        fun getTransactionId(
            @LLMDescription(description = "The order number") orderNumber: String
        ): String = "TXN-$orderNumber-${System.currentTimeMillis()}"
    }
}
