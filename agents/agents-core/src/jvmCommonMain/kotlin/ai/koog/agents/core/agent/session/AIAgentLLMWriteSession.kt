@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.session

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.PromptBuilderAction
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.runBlockingOnStrategyDispatcher
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.jdk9.asPublisher
import kotlinx.serialization.KSerializer
import java.util.concurrent.Flow.Publisher

/**
 * JVM actual implementation of a mutable LLM session.
 *
 * In addition to common suspend APIs, this class exposes Java-friendly wrappers
 * that run session operations on the strategy dispatcher.
 */
@OptIn(InternalAgentsApi::class)
public actual class AIAgentLLMWriteSession actual constructor(
    environment: AIAgentEnvironment,
    executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    toolRegistry: ToolRegistry,
    prompt: Prompt,
    model: LLModel,
    responseProcessor: ResponseProcessor?,
    config: AIAgentConfig,
    clock: KoogClock
) : AIAgentLLMWriteSessionCommon(
    environment,
    executor,
    tools,
    toolRegistry,
    prompt,
    model,
    responseProcessor,
    config,
    clock
) {

    /**
     * Appends a prompt using the provided prompt update action.
     *
     * @param promptUpdate A lambda expression defining the modifications to apply to the prompt.
     */
    @JavaAPI
    @JvmName("appendPrompt")
    public fun javaNonExtensionAppendPrompt(
        promptUpdate: PromptBuilderAction
    ) {
        appendPrompt {
            promptUpdate.build(this)
        }
    }

    @JavaAPI
    @JvmName("requestLLM")
    public fun requestLLMBlocking(): Message.Assistant = config.runBlockingOnStrategyDispatcher {
        this@AIAgentLLMWriteSession.requestLLM()
    }

    @JavaAPI
    @JvmName("requestLLMWithoutTools")
    public fun requestLLMWithoutToolsBlocking(): Message.Assistant =
        config.runBlockingOnStrategyDispatcher {
            requestLLMWithoutTools()
        }

    @JavaAPI
    @JvmName("requestLLMOnlyCallingTools")
    public fun requestLLMOnlyCallingToolsBlocking(): Message.Assistant =
        config.runBlockingOnStrategyDispatcher {
            requestLLMOnlyCallingTools()
        }

    @JavaAPI
    @JvmName("requestLLMForceOneTool")
    public fun requestLLMForceOneToolBlocking(
        tool: ToolDescriptor
    ): Message.Assistant = config.runBlockingOnStrategyDispatcher {
        requestLLMForceOneTool(tool)
    }

    @JavaAPI
    @JvmName("requestLLMForceOneTool")
    public fun requestLLMForceOneToolBlocking(
        tool: ToolBase<*, *>
    ): Message.Assistant = config.runBlockingOnStrategyDispatcher {
        requestLLMForceOneTool(tool)
    }

    @JavaAPI
    @JvmName("requestLLMStreaming")
    public fun requestLLMStreamingBlocking(): Publisher<StreamFrame> =
        config.runBlockingOnStrategyDispatcher {
            requestLLMStreaming().asPublisher()
        }

    @JavaAPI
    @JvmName("requestLLMStreaming")
    public fun requestLLMStreamingBlocking(
        structureDefinition: StructureDefinition
    ): Publisher<StreamFrame> = config.runBlockingOnStrategyDispatcher {
        requestLLMStreaming(structureDefinition).asPublisher()
    }

    @JavaAPI
    @JvmOverloads
    @JvmName("requestModeration")
    public fun requestModerationBlocking(
        moderatingModel: LLModel? = null
    ): ModerationResult = config.runBlockingOnStrategyDispatcher {
        requestModeration(moderatingModel)
    }

    @JavaAPI
    @JvmOverloads
    @JvmName("requestLLMStructured")
    public fun <T> requestLLMStructuredBlocking(
        config: StructuredRequestConfig<T>,
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> = this.config.runBlockingOnStrategyDispatcher {
        requestLLMStructured(config, fixingParser)
    }

    @JavaAPI
    @JvmOverloads
    @JvmName("requestLLMStructured")
    public fun <T> requestLLMStructuredBlocking(
        serializer: KSerializer<T>,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> = config.runBlockingOnStrategyDispatcher {
        requestLLMStructured(serializer, examples, fixingParser)
    }

    @JavaAPI
    @JvmOverloads
    @JvmName("parseResponseToStructuredResponse")
    public fun <T> parseResponseToStructuredResponseBlocking(
        response: Message.Assistant,
        config: StructuredRequestConfig<T>,
        fixingParser: StructureFixingParser? = null
    ): StructuredResponse<T> = this.config.runBlockingOnStrategyDispatcher {
        parseResponseToStructuredResponse(response, config, fixingParser)
    }

    @JavaAPI
    @JvmName("requestLLMMultipleChoices")
    public fun requestLLMMultipleChoicesBlocking(): LLMChoice =
        config.runBlockingOnStrategyDispatcher {
            requestLLMMultipleChoices()
        }

    @JavaAPI
    @JvmOverloads
    @JvmName("replaceHistoryWithTLDR")
    public fun replaceHistoryWithTLDRBlocking(
        strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
        preserveMemory: Boolean = true
    ) {
        config.runBlockingOnStrategyDispatcher {
            replaceHistoryWithTLDR(strategy, preserveMemory)
        }
    }
}
