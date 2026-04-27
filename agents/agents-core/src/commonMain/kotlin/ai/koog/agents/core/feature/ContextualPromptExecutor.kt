package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.ExecuteHook
import ai.koog.prompt.executor.model.ExecutionArgOverrides
import ai.koog.prompt.executor.model.ExecutionArgOverrides.NoOverrides
import ai.koog.prompt.executor.model.ExecutionIntent
import ai.koog.prompt.executor.model.ExecutorHook
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.ModerationHook
import ai.koog.prompt.executor.model.MultipleChoicesHook
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.ResolvedExecutionIntent
import ai.koog.prompt.executor.model.SimpleExecutorHook
import ai.koog.prompt.executor.model.StreamingExecutorHook
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A wrapper around [ai.koog.prompt.executor.model.PromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [ai.koog.prompt.executor.model.PromptExecutor] to wrap;
 * @property context The [AIAgentContext] associated with the agent that is executing the prompt.
 */
@InternalAgentsApi
public class ContextualPromptExecutor(
    private val executor: PromptExecutor,
    private val context: AIAgentContext,
) : PromptExecutor() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: ExecuteHook?
    ): List<Message.Response> {
        val eventId = eventId()
        return executor.execute(
            prompt = prompt,
            model = model,
            tools = tools,
            hook = object : SimpleExecutorHook<List<Message.Response>> {
                override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                    handleModelChoiceFailure(intent, error, eventId, hook)

                override suspend fun beforeExecution(
                    intent: InitialExecutionIntent,
                    effectiveModel: LLModel
                ): ExecutionArgOverrides = beforeNonStreamingCall(intent, effectiveModel, eventId, hook)

                override suspend fun onCompleted(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    result: List<Message.Response>
                ) {
                    logger.trace { "Finished LLM call (event id: $eventId) with responses: [${result.joinToString { "${it.role}: ${it.content}" }}]" }
                    context.pipeline.onLLMCallCompleted(
                        eventId = eventId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = intent.prompt,
                        model = effectiveModel,
                        tools = intent.tools,
                        responses = result,
                        moderationResponse = null,
                        context = context
                    )
                    hook?.onCompleted(intent, effectiveModel, result)
                }

                override suspend fun onFailure(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    error: Throwable
                ) {
                    logger.debug(error) { "Error in executing LLM call (event id: $eventId): $error" }
                    context.pipeline.onLLMCallFailed(
                        eventId,
                        context.executionInfo,
                        context.runId,
                        intent.prompt,
                        effectiveModel,
                        intent.tools,
                        context,
                        error = error
                    )
                    hook?.onFailure(intent, effectiveModel, error)
                }
            }
        )
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: StreamingExecutorHook?
    ): Flow<StreamFrame> {
        val eventId = eventId()
        return executor.executeStreaming(
            prompt = prompt,
            model = model,
            tools = tools,
            hook = object : StreamingExecutorHook {
                override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                    handleModelChoiceFailure(intent, error, eventId, hook)

                override suspend fun beforeExecution(
                    intent: InitialExecutionIntent,
                    effectiveModel: LLModel
                ): ExecutionArgOverrides {
                    logger.debug {
                        "Executing LLM streaming call (event id: $eventId, prompt: ${intent.prompt}, tools: [${intent.tools.joinToString { it.name }}]," +
                            " requested model: ${intent.model.id}, effective model: ${effectiveModel.id})"
                    }
                    val promptBeforeInterceptors = context.llm.prompt

                    context.pipeline.onLLMStreamingStarting(
                        eventId = eventId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = intent.prompt,
                        model = effectiveModel,
                        tools = intent.tools,
                        context = context
                    )

                    val outerOverrides = hook?.beforeExecution(intent, effectiveModel)
                    return potentialPromptOverride(promptBeforeInterceptors, intent, outerOverrides, eventId)
                }

                override suspend fun onFrame(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    frame: StreamFrame
                ) {
                    logger.trace { "Received frame from LLM streaming call (event id: $eventId): $frame" }
                    context.pipeline.onLLMStreamingFrameReceived(
                        eventId = eventId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = intent.prompt,
                        model = effectiveModel,
                        streamFrame = frame,
                        context = context
                    )
                    hook?.onFrame(intent, effectiveModel, frame)
                }

                override suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel) {
                    logger.debug { "Finished LLM streaming call (event id: $eventId)" }
                    context.pipeline.onLLMStreamingCompleted(
                        eventId = eventId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = intent.prompt,
                        model = effectiveModel,
                        tools = intent.tools,
                        context = context
                    )
                    hook?.onCompleted(intent, effectiveModel)
                }

                override suspend fun onFailure(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    error: Throwable
                ) {
                    logger.debug(error) { "Error in LLM streaming call (event id: $eventId): $error" }
                    context.pipeline.onLLMStreamingFailed(
                        eventId = eventId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = intent.prompt,
                        model = effectiveModel,
                        throwable = error,
                        context = context
                    )
                    hook?.onFailure(intent, effectiveModel, error)
                }
            }
        )
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: MultipleChoicesHook?
    ): List<LLMChoice> {
        val eventId = eventId()
        return executor.executeMultipleChoices(
            prompt = prompt,
            model = model,
            tools = tools,
            hook = object : SimpleExecutorHook<List<LLMChoice>> {
                override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                    handleModelChoiceFailure(intent, error, eventId, hook)

                // TODO: Add Pipeline interceptors for this method. Without them features cannot modify prompts before calls to LLMs.
                override suspend fun beforeExecution(
                    intent: InitialExecutionIntent,
                    effectiveModel: LLModel
                ): ExecutionArgOverrides =
                    beforeNonStreamingCall(intent, effectiveModel, eventId, hook, ignorePipeline = true)

                override suspend fun onCompleted(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    result: List<LLMChoice>
                ) {
                    logger.debug {
                        val messageBuilder = StringBuilder().appendLine("Finished LLM call with LLM Choice response:")
                        result.forEachIndexed { index, response ->
                            messageBuilder.appendLine("- Response #$index")
                            response.forEach { message ->
                                messageBuilder.appendLine("  -- [${message.role}] ${message.content}")
                            }
                        }
                        "Finished LLM call with responses: $messageBuilder"
                    }
                    hook?.onCompleted(intent, effectiveModel, result)
                }

                override suspend fun onFailure(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    error: Throwable
                ) {
                    hook?.onFailure(intent, effectiveModel, error)
                }
            }
        )
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        hook: ModerationHook?
    ): ModerationResult {
        val eventId = eventId()
        return executor.moderate(
            prompt = prompt,
            model = model,
            hook = object : SimpleExecutorHook<ModerationResult> {
                override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                    handleModelChoiceFailure(intent, error, eventId, hook)

                override suspend fun beforeExecution(
                    intent: InitialExecutionIntent,
                    effectiveModel: LLModel
                ): ExecutionArgOverrides =
                    beforeNonStreamingCall(intent, effectiveModel, eventId, hook)

                override suspend fun onCompleted(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    result: ModerationResult
                ) {
                    logger.trace { "Finished moderation LLM request (event id: $eventId) with response: $result" }
                    context.pipeline.onLLMCallCompleted(
                        eventId = eventId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = intent.prompt,
                        model = effectiveModel,
                        tools = intent.tools,
                        responses = emptyList(),
                        moderationResponse = result,
                        context = context
                    )
                    hook?.onCompleted(intent, effectiveModel, result)
                }

                override suspend fun onFailure(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    error: Throwable
                ) {
                    context.pipeline.onLLMCallFailed(
                        eventId,
                        context.executionInfo,
                        context.runId,
                        intent.prompt,
                        effectiveModel,
                        intent.tools,
                        context,
                        error = error
                    )
                    hook?.onFailure(intent, effectiveModel, error)
                }
            }
        )
    }

    override suspend fun models(): List<LLModel> = executor.models()

    override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator {
        return executor.getStandardJsonSchemaGenerator(model)
    }

    override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator {
        return executor.getBasicJsonSchemaGenerator(model)
    }

    override fun close() {
        executor.close()
    }

    private fun eventId(): String {
        @OptIn(ExperimentalUuidApi::class)
        return Uuid.random().toString()
    }

    private suspend fun handleModelChoiceFailure(
        intent: InitialExecutionIntent,
        error: Throwable,
        eventId: String,
        outerHook: ExecutorHook?
    ) {
        logger.debug {
            "Failed to choose model for LLM call (event id: $eventId, prompt: ${intent.prompt}, tools: [${intent.tools.joinToString { it.name }}]," +
                " requested model: ${intent.model.id}, error: $error)"
        }
        outerHook?.onModelChoiceFailed(intent, error)
    }

    private suspend fun beforeNonStreamingCall(
        intent: InitialExecutionIntent,
        effectiveModel: LLModel,
        eventId: String,
        outerHook: SimpleExecutorHook<*>?,
        ignorePipeline: Boolean = false, // TODO: utilized only for executeMultipleChoices, remove once corresponding pipeline interceptors are added
    ): ExecutionArgOverrides {
        logger.debug {
            "Starting LLM call (event id: $eventId, prompt: ${intent.prompt}, tools: [${intent.tools.joinToString { it.name }}]," +
                " requested model: ${intent.model.id}, effective model: ${effectiveModel.id})"
        }

        if (ignorePipeline) {
            return outerHook?.beforeExecution(intent, effectiveModel) ?: NoOverrides
        }

        val promptBeforeInterceptors = context.llm.prompt
        context.pipeline.onLLMCallStarting(
            eventId = eventId,
            executionInfo = context.executionInfo,
            runId = context.runId,
            prompt = intent.prompt,
            model = effectiveModel,
            tools = intent.tools,
            context = context
        )

        val outerOverrides = outerHook?.beforeExecution(intent, effectiveModel)
        return potentialPromptOverride(promptBeforeInterceptors, intent, outerOverrides, eventId)
    }

    private fun potentialPromptOverride(
        promptBeforeInterceptors: Prompt,
        intent: ExecutionIntent,
        outerOverrides: ExecutionArgOverrides?,
        eventId: String
    ): ExecutionArgOverrides {
        val nestedOverrides = if (promptBeforeInterceptors !== context.llm.prompt) {
            logger.debug { "Executing LLM call with modified prompt (event id: $eventId, prompt: ${context.llm.prompt}, tools: [${intent.tools.joinToString { it.name }}])" }
            ExecutionArgOverrides.UseDifferentPrompt(context.llm.prompt)
        } else {
            logger.debug { "Executing LLM call prompt (event id: $eventId, prompt: ${context.llm.prompt}, tools: [${intent.tools.joinToString { it.name }}])" }
            NoOverrides
        }

        // Pipeline override takes priority: if a feature modified context.llm.prompt,
        // that wins over any prompt substitution requested by the outer hook.
        return when (outerOverrides) {
            null -> nestedOverrides
            else -> outerOverrides.combineWith(nestedOverrides)
        }
    }
}
