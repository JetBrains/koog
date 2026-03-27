package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.ExecutionArgOverrides
import ai.koog.prompt.executor.model.ExecutionArgOverrides.NoOverrides
import ai.koog.prompt.executor.model.ExecutionIntent
import ai.koog.prompt.executor.model.ExecutorHook
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorHooks
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
        hooks: PromptExecutorHooks?
    ): List<Message.Response> {
        return executor.execute(
            prompt = prompt,
            model = model,
            tools = tools,
            hooks = ContextualPromptExecutorHooks(eventId(), outerHooks = hooks)
        )
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hooks: PromptExecutorHooks?
    ): Flow<StreamFrame> {
        return executor.executeStreaming(
            prompt = prompt,
            model = model,
            tools = tools,
            hooks = ContextualPromptExecutorHooks(eventId(), outerHooks = hooks)
        )
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hooks: PromptExecutorHooks?
    ): List<LLMChoice> =
        executor.executeMultipleChoices(
            prompt = prompt,
            model = model,
            tools = tools,
            hooks = ContextualPromptExecutorHooks(eventId(), outerHooks = hooks)
        )

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        hooks: PromptExecutorHooks?
    ): ModerationResult {
        return executor.moderate(
            prompt = prompt,
            model = model,
            hooks = ContextualPromptExecutorHooks(eventId(), outerHooks = hooks)
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

    private inner class ContextualPromptExecutorHooks(
        private val eventId: String,
        private val outerHooks: PromptExecutorHooks?,
    ) : PromptExecutorHooks {
        override val execute = object : SimpleExecutorHook<List<Message.Response>> {
            override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                handleModelChoiceFailure(intent, error, outerHooks?.execute)

            override suspend fun beforeExecution(
                intent: InitialExecutionIntent,
                effectiveModel: LLModel
            ): ExecutionArgOverrides = beforeNonStreamingCall(intent, effectiveModel, outerHooks?.execute)

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
                outerHooks?.execute?.onCompleted(intent, effectiveModel, result)
            }

            override suspend fun onFailure(
                intent: ResolvedExecutionIntent,
                effectiveModel: LLModel,
                error: Throwable
            ) {
                outerHooks?.execute?.onFailure(intent, effectiveModel, error)
            }
        }

        override val multipleChoices: SimpleExecutorHook<List<LLMChoice>> =
            object : SimpleExecutorHook<List<LLMChoice>> {
                override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                    handleModelChoiceFailure(intent, error, outerHooks?.multipleChoices)

                // TODO: Add Pipeline interceptors for this method. Without them features cannot modify prompts before calls to LLMs.
                override suspend fun beforeExecution(
                    intent: InitialExecutionIntent,
                    effectiveModel: LLModel
                ): ExecutionArgOverrides =
                    beforeNonStreamingCall(intent, effectiveModel, outerHooks?.multipleChoices, ignorePipeline = true)

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
                    outerHooks?.multipleChoices?.onCompleted(intent, effectiveModel, result)
                }

                override suspend fun onFailure(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    error: Throwable
                ) {
                    outerHooks?.multipleChoices?.onFailure(intent, effectiveModel, error)
                }
            }

        override val moderation: SimpleExecutorHook<ModerationResult> = object : SimpleExecutorHook<ModerationResult> {
            override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                handleModelChoiceFailure(intent, error, outerHooks?.moderation)

            override suspend fun beforeExecution(
                intent: InitialExecutionIntent,
                effectiveModel: LLModel
            ): ExecutionArgOverrides =
                beforeNonStreamingCall(intent, effectiveModel, outerHooks?.moderation)

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
                outerHooks?.moderation?.onCompleted(intent, effectiveModel, result)
            }

            override suspend fun onFailure(intent: ResolvedExecutionIntent, effectiveModel: LLModel, error: Throwable) {
                outerHooks?.moderation?.onFailure(intent, effectiveModel, error)
            }
        }

        override val streaming: StreamingExecutorHook = object : StreamingExecutorHook {
            override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                handleModelChoiceFailure(intent, error, outerHooks?.streaming)

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

                val outerOverrides = outerHooks?.streaming?.beforeExecution(intent, effectiveModel)
                return potentialPromptOverride(promptBeforeInterceptors, intent, outerOverrides)
            }

            override suspend fun onFrame(intent: ResolvedExecutionIntent, effectiveModel: LLModel, frame: StreamFrame) {
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
                outerHooks?.streaming?.onFrame(intent, effectiveModel, frame)
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
                outerHooks?.streaming?.onCompleted(intent, effectiveModel)
            }

            override suspend fun onFailure(intent: ResolvedExecutionIntent, effectiveModel: LLModel, error: Throwable) {
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
                outerHooks?.streaming?.onFailure(intent, effectiveModel, error)
            }
        }

        private suspend fun handleModelChoiceFailure(
            intent: InitialExecutionIntent,
            error: Throwable,
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
            return potentialPromptOverride(promptBeforeInterceptors, intent, outerOverrides)
        }

        private fun potentialPromptOverride(
            promptBeforeInterceptors: Prompt,
            intent: ExecutionIntent,
            outerOverrides: ExecutionArgOverrides?
        ): ExecutionArgOverrides {
            val nestedOverrides = if (promptBeforeInterceptors !== context.llm.prompt) {
                logger.debug { "Executing LLM call with modified prompt (event id: $eventId, prompt: ${context.llm.prompt}, tools: [${intent.tools.joinToString { it.name }}])" }
                ExecutionArgOverrides.UseDifferentPrompt(context.llm.prompt)
            } else {
                logger.debug { "Executing LLM call prompt (event id: $eventId, prompt: ${context.llm.prompt}, tools: [${intent.tools.joinToString { it.name }}])" }
                NoOverrides
            }

            return when (outerOverrides) {
                null -> nestedOverrides
                else -> outerOverrides.combineWith(nestedOverrides)
            }
        }
    }
}
