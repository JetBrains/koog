@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer

/**
 * Represents a session for interacting with a language model (LLM) in a read-only context within an AI agent setup.
 * This session is configured with a set of tools, an executor for handling prompt execution, a prompt definition,
 * a language model, and specific session configurations.
 *
 * @constructor Internal constructor to initialize a new read session for the AI agent.
 * @param tools A list of tool descriptors that define the tools available for this session.
 * @param executor The `PromptExecutor` responsible for handling execution of prompts within this session.
 * @param prompt The `Prompt` object specifying the input messages and parameters for the session.
 * @param model The language model instance to be used for processing prompts in this session.
 * @param responseProcessor The response processor instance to be used for post-processing responses.
 * @param config The configuration settings for the AI agent session.
 */
public expect class AIAgentLLMReadSession internal constructor(
    tools: List<ToolDescriptor>,
    executor: PromptExecutor,
    prompt: Prompt,
    model: LLModel,
    responseProcessor: ResponseProcessor?,
    config: AIAgentConfig,
) : AIAgentLLMSession {
    override val config: AIAgentConfig
    override val prompt: Prompt
    override val tools: List<ToolDescriptor>
    override val model: LLModel
    override val responseProcessor: ResponseProcessor?

    @InternalAgentsApi
    override var isActive: Boolean

    @InternalAgentsApi
    override fun validateSession()

    @InternalAgentsApi
    override fun preparePrompt(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): Prompt

    @InternalAgentsApi
    override fun executeStreaming(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame>

    @InternalAgentsApi
    override suspend fun executeMultiple(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): List<Message.Response>

    @InternalAgentsApi
    override suspend fun executeSingle(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): Message.Response

    override suspend fun requestLLMMultipleWithoutTools(): List<Message.Response>
    override suspend fun requestLLMWithoutTools(): Message.Response
    override suspend fun requestLLMOnlyCallingTools(): Message.Response
    override suspend fun requestLLMMultipleOnlyCallingTools(): List<Message.Response>
    override suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response
    override suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response
    override suspend fun requestLLM(): Message.Response
    override suspend fun requestLLMStreaming(): Flow<StreamFrame>
    override suspend fun requestModeration(moderatingModel: LLModel?): ModerationResult
    override suspend fun requestLLMMultiple(): List<Message.Response>
    override suspend fun <T> requestLLMStructured(config: StructuredRequestConfig<T>): Result<StructuredResponse<T>>
    override suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T>,
        fixingParser: StructureFixingParser?
    ): Result<StructuredResponse<T>>

    override suspend fun <T> parseResponseToStructuredResponse(
        response: Message.Assistant,
        config: StructuredRequestConfig<T>
    ): StructuredResponse<T>

    override suspend fun requestLLMMultipleChoices(): List<LLMChoice>
}
