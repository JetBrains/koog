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
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.ModerateHook
import ai.koog.prompt.executor.model.MultipleChoicesHook
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.ResolvedExecutionIntent
import ai.koog.prompt.executor.model.StreamingHook
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
    private val executor: HookablePromptExecutor,
    private val context: AIAgentContext,
) : PromptExecutor() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> =
        executor.execute(prompt, model, tools, hook = ContextualPromptExecutorHooks.executeHook(eventId(), context))

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> =
        executor.executeStreaming(prompt, model, tools, hook = ContextualPromptExecutorHooks.streamingHook(eventId(), context))

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<LLMChoice> =
        executor.executeMultipleChoices(prompt, model, tools, hook = ContextualPromptExecutorHooks.multipleChoicesHook(eventId(), context))

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult =
        executor.moderate(prompt, model, hook = ContextualPromptExecutorHooks.moderationHook(eventId(), context))

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

    private object ContextualPromptExecutorHooks {

        fun executeHook(eventId: String, context: AIAgentContext) = object : ExecuteHook {
            override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                handleModelChoiceFailure(eventId, intent, error)

            override suspend fun beforeExecution(
                intent: InitialExecutionIntent,
                effectiveModel: LLModel
            ): ExecutionArgOverrides = beforeNonStreamingCall(eventId, context, intent, effectiveModel)

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
            }
        }

        fun multipleChoicesHook(eventId: String, context: AIAgentContext) = object : MultipleChoicesHook {
            override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                handleModelChoiceFailure(eventId, intent, error)

            // TODO: Add Pipeline interceptors for this method. Without them features cannot modify prompts before calls to LLMs.
            override suspend fun beforeExecution(
                intent: InitialExecutionIntent,
                effectiveModel: LLModel
            ): ExecutionArgOverrides = beforeNonStreamingCall(eventId, context, intent, effectiveModel, ignorePipeline = true)

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
            }
        }

        fun moderationHook(eventId: String, context: AIAgentContext) = object : ModerateHook {
            override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                handleModelChoiceFailure(eventId, intent, error)

            override suspend fun beforeExecution(
                intent: InitialExecutionIntent,
                effectiveModel: LLModel
            ): ExecutionArgOverrides = beforeNonStreamingCall(eventId, context, intent, effectiveModel)

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
            }
        }

        fun streamingHook(eventId: String, context: AIAgentContext) = object : StreamingHook {
            override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
                handleModelChoiceFailure(eventId, intent, error)

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
                return potentialPromptOverride(eventId, context, promptBeforeInterceptors, intent)
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
            }
        }

        private fun handleModelChoiceFailure(
            eventId: String,
            intent: InitialExecutionIntent,
            error: Throwable
        ) {
            logger.debug {
                "Failed to choose model for LLM call (event id: $eventId, prompt: ${intent.prompt}, tools: [${intent.tools.joinToString { it.name }}]," +
                    " requested model: ${intent.model.id}, error: $error)"
            }
        }

        private suspend fun beforeNonStreamingCall(
            eventId: String,
            context: AIAgentContext,
            intent: InitialExecutionIntent,
            effectiveModel: LLModel,
            ignorePipeline: Boolean = false, // TODO: utilized only for executeMultipleChoices, remove once corresponding pipeline interceptors are added
        ): ExecutionArgOverrides {
            logger.debug {
                "Starting LLM call (event id: $eventId, prompt: ${intent.prompt}, tools: [${intent.tools.joinToString { it.name }}]," +
                    " requested model: ${intent.model.id}, effective model: ${effectiveModel.id})"
            }
            if (ignorePipeline) {
                return NoOverrides
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
            return potentialPromptOverride(eventId, context, promptBeforeInterceptors, intent)
        }

        private fun potentialPromptOverride(
            eventId: String,
            context: AIAgentContext,
            promptBeforeInterceptors: Prompt,
            intent: ExecutionIntent
        ): ExecutionArgOverrides {
            return if (promptBeforeInterceptors !== context.llm.prompt) {
                logger.debug { "Executing LLM call with modified prompt (event id: $eventId, prompt: ${context.llm.prompt}, tools: [${intent.tools.joinToString { it.name }}])" }
                ExecutionArgOverrides.UseDifferentPrompt(context.llm.prompt)
            } else {
                logger.debug { "Executing LLM call prompt (event id: $eventId, prompt: ${context.llm.prompt}, tools: [${intent.tools.joinToString { it.name }}])" }
                NoOverrides
            }
        }
    }
}
