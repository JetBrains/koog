@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalPromptAPI::class)

package ai.koog.prompt.executor.model

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.annotations.InternalPromptAPI
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.runBlockingReentrant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.jdk9.asPublisher
import java.util.concurrent.Flow.Publisher
import ai.koog.prompt.executor.model.factory.PromptExecutorBuilder as JvmPromptExecutorFactory

@Suppress("MissingKDocForPublicAPI")
public actual class PromptExecutor internal actual constructor(
    @property:InternalPromptAPI public actual val builder: PromptExecutorBuilder,
) : PromptExecutorAPI {

    public actual fun resolveModel(model: LLModel, operation: PromptExecutorOperation): LLModel =
        builder.resolveModel(model, operation)

    @JvmSynthetic
    actual override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant =
        builder.onExecute(prompt, builder.resolveModel(model, PromptExecutorOperation.Execute), tools)

    @JvmSynthetic
    actual override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> =
        builder.onStreaming(prompt, builder.resolveModel(model, PromptExecutorOperation.Streaming), tools)

    @JvmSynthetic
    actual override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): LLMChoice =
        builder.onMultipleChoices(prompt, builder.resolveModel(model, PromptExecutorOperation.MultipleChoices), tools)

    @JvmSynthetic
    actual override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        builder.onModerate(prompt, builder.resolveModel(model, PromptExecutorOperation.Moderate))

    @JvmSynthetic
    actual override suspend fun models(): List<LLModel> = builder.onModels()

    actual override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator =
        builder.getStandardJsonSchemaGenerator(model)

    actual override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator =
        builder.getBasicJsonSchemaGenerator(model)

    actual override fun close() {
        builder.onClose()
    }

    /**
     * Executes a given prompt using the specified LLM and tools, returning a list of responses from the model.
     */
    @OptIn(InternalKoogUtils::class)
    @JavaAPI
    @JvmOverloads
    @JvmName("execute")
    public fun executeBlocking(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): Message.Assistant = runBlockingReentrant {
        execute(prompt, model, tools)
    }

    /**
     * Receives multiple independent choices from the LLM.
     */
    @OptIn(InternalKoogUtils::class)
    @JavaAPI
    @JvmOverloads
    @JvmName("executeMultipleChoices")
    public fun executeMultipleChoicesBlocking(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): LLMChoice = runBlockingReentrant {
        executeMultipleChoices(prompt, model, tools)
    }

    /**
     * Executes a given prompt using the specified language model (LLM) and tools,
     * providing the results as a synchronous stream of [StreamFrame] objects.
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("executeStreaming")
    public fun executeStreamingBlocking(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList(),
    ): Publisher<StreamFrame> = executeStreaming(prompt, model, tools).asPublisher()

    /**
     * Moderates the content of a given message with attachments using a specified LLM.
     */
    @OptIn(InternalKoogUtils::class)
    @JavaAPI
    @JvmName("moderate")
    public fun moderateBlocking(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = runBlockingReentrant {
        moderate(prompt, model)
    }

    /**
     * Retrieves a list of available models from all LLM clients managed by this executor.
     */
    @OptIn(InternalKoogUtils::class)
    @JavaAPI
    @JvmName("models")
    public fun modelsBlocking(): List<LLModel> = runBlockingReentrant {
        models()
    }

    /**
     * Companion object for [PromptExecutor].
     */
    public companion object {

        /**
         * Creates a Java-friendly factory for constructing a [PromptExecutor] from registered LLM
         * clients.
         *
         * The concrete executor implementation is chosen automatically at build time based on the
         * registered clients — see [JvmPromptExecutorFactory.build] for the selection heuristic.
         *
         * Example usage in Java:
         * ```java
         * PromptExecutor executor = PromptExecutor.builder()
         *     .addClient(openAIClient)
         *     .addClient(anthropicClient)
         *     .build();
         * ```
         */
        @JvmStatic
        @JavaAPI
        public fun builder(): JvmPromptExecutorFactory = JvmPromptExecutorFactory()
    }
}
