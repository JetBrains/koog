@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.context

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.utils.runBlockingOnLLMDispatcher
import ai.koog.agents.core.utils.runBlockingOnStrategyDispatcher
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.utils.annotations.InternalKoogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import java.util.concurrent.Flow
import kotlin.reflect.KClass

@Suppress("MissingKDocForPublicAPI")
@OptIn(InternalKoogUtils::class)
public actual abstract class AIAgentFunctionalContextBase<Pipeline : AIAgentPipeline> internal actual constructor(
    environment: AIAgentEnvironment,
    agentId: String,
    runId: String,
    agentInput: Any?,
    config: AIAgentConfig,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    strategyName: String,
    pipeline: Pipeline,
    executionInfo: AgentExecutionInfo,
    parentContext: AIAgentContext?,
    storeMap: MutableMap<AIAgentStorageKey<*>, Any>
) : AIAgentFunctionalContextBaseCommon<Pipeline>(
    environment = environment,
    agentId = agentId,
    runId = runId,
    agentInput = agentInput,
    config = config,
    llm = llm,
    stateManager = stateManager,
    storage = storage,
    strategyName = strategyName,
    pipeline = pipeline,
    executionInfo = executionInfo,
    storeMap = storeMap,
    parentContext = parentContext
) {

    /**
     * Retrieves the current AI agent environment.
     *
     * @return The AIAgentEnvironment instance representing the current environment.
     */
    @JavaAPI
    public fun environment(): AIAgentEnvironment = environment

    /**
     * Retrieves the unique identifier for the agent.
     *
     * @return A string representing the agent's unique identifier.
     */
    @JavaAPI
    public fun agentId(): String = agentId

    /**
     * Builds and returns an instance of the AIAgentFunctionalPipeline.
     *
     * @return An instance of AIAgentFunctionalPipeline representing the constructed pipeline.
     */
    @JavaAPI
    public fun pipeline(): Pipeline = pipeline

    /**
     * Retrieves the current run identifier.
     *
     * @return the run identifier as a string.
     */
    @JavaAPI
    public fun runId(): String = runId

    /**
     * Retrieves the current agent input.
     *
     * @return The agent input as an `Any?` type, which may be null if no input is available.
     */
    @JavaAPI
    public fun agentInput(): Any? = agentInput

    /**
     * Provides the configuration for an AI agent.
     *
     * @return An instance of [AIAgentConfig] representing the current AI agent configuration.
     */
    @JavaAPI
    public fun config(): AIAgentConfig = config

    /**
     * Returns the current instance of AIAgentLLMContext.
     *
     * @return The AIAgentLLMContext instance.
     */
    @JavaAPI
    public fun llm(): AIAgentLLMContext = llm

    /**
     * Provides an instance of AIAgentStateManager responsible for managing the state
     * of an AI Agent. This function allows access to the state management operations
     * for coordinating AI agent behavior.
     *
     * @return the AIAgentStateManager instance.
     */
    @JavaAPI
    public fun stateManager(): AIAgentStateManager = stateManager

    /**
     * Provides access to the AIAgentStorage instance.
     *
     * @return the AIAgentStorage instance used for managing AI agent-related data.
     */
    @JavaAPI
    public fun storage(): AIAgentStorage = storage

    /**
     * Retrieves the name of the strategy.
     *
     * @return the name of the strategy as a string.
     */
    @JavaAPI
    public fun strategyName(): String = strategyName

    /**
     * Retrieves the execution information of the agent.
     *
     * @return an instance of AgentExecutionInfo containing details about the execution context.
     */
    @JavaAPI
    public fun executionInfo(): AgentExecutionInfo = executionInfo

    @JavaAPI
    @JvmName("getHistory")
    public fun getHistoryBlocking(): List<Message> = config.runBlockingOnLLMDispatcher {
        getHistory()
    }

    /**
     * Sends a request to the Large Language Model (LLM) and retrieves its response.
     *
     * @param message The input message to be sent to the LLM.
     * @param allowToolCalls Determines whether the LLM is allowed to use tools during its response generation.
     *                       Defaults to true.
     * @return A [Message.Response] object containing the message received from the LLM.
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("requestLLM")
    public fun requestLLMBlocking(
        message: String,
        allowToolCalls: Boolean = true
    ): Message.Response = config.runBlockingOnLLMDispatcher {
        requestLLM(message, allowToolCalls)
    }

    /**
     * Retrieves the most recent token usage count synchronously.
     *
     * @return The latest token usage count as an integer.
     */
    @JavaAPI
    @JvmName("latestTokenUsage")
    public fun latestTokenUsageBlocking(): Int = config.runBlockingOnLLMDispatcher {
        latestTokenUsage()
    }

    /**
     * Sends a structured request to the Large Language Model (LLM) and processes the response.
     *
     * @param T The type of the structured response required.
     * @param message The input message to be sent to the LLM.
     * @param clazz The Kotlin class type of the expected structured response.
     * @param examples An optional list of example objects used to guide the model's structured response generation.
     * @param fixingParser An optional parser to correct or validate the structured response.
     * @return A [Result] containing a [StructuredResponse] of the requested type [T], or an error if the request fails.
     */
    @JavaAPI
    @JvmOverloads
    @OptIn(InternalSerializationApi::class)
    public suspend fun <T : Any> requestLLMStructured(
        message: String,
        clazz: KClass<T>,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> =
        requestLLMStructured(message, clazz.serializer(), examples, fixingParser)

    /**
     * Sends a request to the Language Learning Model (LLM) for streaming data.
     *
     * @param message The message or query to be sent to the LLM for processing.
     * @param structureDefinition An optional parameter specifying the structured data definition for parsing or validating the response.
     * @return A `Publisher` that emits `StreamFrame` objects representing the streamed response from the LLM.
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("requestLLMStreaming")
    public fun requestLLMStreamingBlocking(
        message: String,
        structureDefinition: StructureDefinition? = null
    ): Flow.Publisher<StreamFrame> {
        val dispatcher = config.llmRequestDispatcher

        return Flow.Publisher { subscriber ->
            val scope = CoroutineScope(dispatcher)
            val job = scope.launch {
                try {
                    requestLLMStreaming(message, structureDefinition).collect { frame ->
                        subscriber.onNext(frame)
                    }
                    subscriber.onComplete()
                } catch (e: Throwable) {
                    subscriber.onError(e)
                }
            }
            subscriber.onSubscribe(object : Flow.Subscription {
                override fun request(n: Long) {
                    // Basic implementation without backpressure handling for simplicity.
                    // For production, consider adding flow control (e.g., using a shared flow or buffer).
                }

                override fun cancel() {
                    job.cancel()
                }
            })
        }
    }

    /**
     * Sends a request to the Large Language Model (LLM) and retrieves multiple responses.
     *
     * @param message The input message to be sent to the LLM.
     * @return A list of [Message.Response] objects containing the LLM responses to the provided message.
     */
    @JavaAPI
    @JvmName("requestLLMMultiple")
    public fun requestLLMMultipleBlocking(
        message: String
    ): List<Message.Response> = config.runBlockingOnLLMDispatcher {
        requestLLMMultiple(message)
    }

    /**
     * Executes a request to the LLM, restricting the process to only calling external tools as needed.
     *
     * @param message The input message or query to be processed by the LLM.
     * @return The response generated by the LLM, encapsulated in a Message.Response object.
     */
    @JavaAPI
    @JvmName("requestLLMOnlyCallingTools")
    public fun requestLLMOnlyCallingToolsBlocking(
        message: String
    ): Message.Response = config.runBlockingOnLLMDispatcher {
        requestLLMOnlyCallingTools(message)
    }

    /**
     * Sends a request to the LLM (Large Language Model) system using a specified tool, ensuring the
     * use of exactly one tool in the response generation process.
     *
     * @param message The input message or prompt to be processed by the LLM.
     * @param tool The specific tool descriptor that defines the tool to be used.
     * @return The response generated by the LLM system as a `Message.Response` object.
     */
    @JavaAPI
    @JvmName("requestLLMForceOneTool")
    public fun requestLLMForceOneToolBlocking(
        message: String,
        tool: ToolDescriptor
    ): Message.Response = config.runBlockingOnLLMDispatcher {
        requestLLMForceOneTool(message, tool)
    }

    /**
     * Sends a request to the LLM (Large Language Model) forcing the use of a specified tool and returns the response.
     *
     * @param message The message to be sent to the LLM.
     * @param tool The tool that the LLM is forced to use in processing the message.
     * @return A Message.Response object containing the result from the LLM after processing the request.
     */
    @JavaAPI
    @JvmName("requestLLMForceOneTool")
    public fun requestLLMForceOneToolBlocking(
        message: String,
        tool: Tool<*, *>
    ): Message.Response = config.runBlockingOnLLMDispatcher {
        requestLLMForceOneTool(message, tool)
    }

    /**
     * Executes the specified tool call.
     *
     * @param toolCall the tool call to be executed
     * @return the result of the executed tool call
     */
    @JavaAPI
    @JvmName("executeTool")
    public fun executeToolBlocking(
        toolCall: Message.Tool.Call
    ): ReceivedToolResult = config.runBlockingOnStrategyDispatcher {
        executeTool(toolCall)
    }

    /**
     * Executes multiple tool calls either sequentially or in parallel based on the provided configurations.
     *
     * @param toolCalls a list of tool call objects to be executed
     * @param parallelTools a boolean flag indicating whether the tool calls should be executed in parallel
     * @return a list of results obtained from executing the provided tool calls
     */
    @JavaAPI
    @JvmName("executeMultipleTools")
    public fun executeMultipleToolsBlocking(
        toolCalls: List<Message.Tool.Call>,
        parallelTools: Boolean
    ): List<ReceivedToolResult> = config.runBlockingOnStrategyDispatcher {
        executeMultipleTools(toolCalls, parallelTools)
    }

    /**
     * Sends the provided tool result for processing.
     *
     * @param toolResult The result from a tool that is to be sent for further processing.
     * @return A response message object after processing the tool result.
     */
    @JavaAPI
    @JvmName("sendToolResult")
    public fun sendToolResultBlocking(
        toolResult: ReceivedToolResult
    ): Message.Response = config.runBlockingOnLLMDispatcher {
        sendToolResult(toolResult)
    }

    /**
     * Sends multiple tool results for processing and returns the corresponding responses.
     *
     * @param results A list of ReceivedToolResult representing the tool results to be processed.
     * @return A list of Message.Response objects representing the responses after processing the tool results.
     */
    @JavaAPI
    @JvmName("sendMultipleToolResults")
    public fun sendMultipleToolResultsBlocking(
        results: List<ReceivedToolResult>
    ): List<Message.Response> = config.runBlockingOnLLMDispatcher {
        sendMultipleToolResults(results)
    }

    /**
     * Executes a single tool with the specified arguments.
     *
     * @param tool The tool to be executed.
     * @param toolArgs The arguments to be passed to the tool.
     * @param doUpdatePrompt A flag indicating whether to update the prompt during execution.
     * @return The result of the executed tool wrapped in a SafeTool.Result.
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("executeSingleTool")
    public fun <ToolArg, ToolResult> executeSingleToolBlocking(
        tool: Tool<ToolArg, ToolResult>,
        toolArgs: ToolArg,
        doUpdatePrompt: Boolean = true
    ): SafeTool.Result<ToolResult> = config.runBlockingOnStrategyDispatcher {
        executeSingleTool(tool, toolArgs, doUpdatePrompt)
    }

    /**
     * Compresses the historical data of a tool's operations using the specified compression strategy.
     *
     * @param strategy the strategy to use for compressing the history, defaults to the whole history compression strategy
     * @param preserveMemory a flag indicating whether to prioritize memory preservation during compression, defaults to true
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("compressHistory")
    public fun compressHistoryBlocking(
        strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
        preserveMemory: Boolean = true
    ): Unit = config.runBlockingOnLLMDispatcher {
        compressHistory(strategy, preserveMemory)
    }

    @JavaAPI
    public fun subtask(taskDescription: String): SubtaskBuilder = SubtaskBuilder(this, taskDescription)
}
