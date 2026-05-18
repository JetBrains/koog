package ai.koog.agents.core.agent.config

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.jackson.JacksonSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

@OptIn(InternalAgentsApi::class)
@Suppress(
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
    "MissingKDocForPublicAPI",
    "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT"
)
public actual class AIAgentConfig actual constructor(
    public actual override val prompt: Prompt,
    public actual override val model: LLModel,
    public actual val maxAgentIterations: Int,
    public actual val missingToolsConversionStrategy: MissingToolsConversionStrategy,
    public actual val responseProcessor: ResponseProcessor?,
    public actual val serializer: JSONSerializer,
) : AIAgentConfigBase {

    /**
     * [CoroutineDispatcher] for running agent strategy logic
     *
     * By default, all agent operations will be performed on [Dispatchers.Default]
     **/
    @JavaAPI
    @InternalAgentsApi
    public var strategyDispatcher: CoroutineDispatcher = Dispatchers.Default
        internal set

    /**
     * IO-bounded [CoroutineDispatcher] for performing LLM communications
     *
     * By default, all IO/LLM operations will be performed on [Dispatchers.IO]
     * */
    @JavaAPI
    @InternalAgentsApi
    public var llmRequestDispatcher: CoroutineDispatcher = Dispatchers.IO
        internal set

    @Deprecated("Use constructor that accepts Executor instead of ExecutorService")
    @JavaAPI
    @JvmOverloads
    public constructor(
        prompt: Prompt,
        model: LLModel,
        maxAgentIterations: Int,
        agentStrategyExecutorService: ExecutorService?,
        llmRequestExecutorService: ExecutorService? = null,
        missingToolsConversionStrategy: MissingToolsConversionStrategy =
            MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON),
        responseProcessor: ResponseProcessor? = null,
        serializer: JSONSerializer = JacksonSerializer()
    ) : this(prompt, model, maxAgentIterations, missingToolsConversionStrategy, responseProcessor, serializer) {
        agentStrategyExecutorService?.let { strategyDispatcher = it.asCoroutineDispatcher() }
        llmRequestExecutorService?.let { llmRequestDispatcher = it.asCoroutineDispatcher() }
    }

    @JavaAPI
    @JvmOverloads
    public constructor(
        prompt: Prompt,
        model: LLModel,
        maxAgentIterations: Int,
        strategyExecutor: Executor?,
        llmRequestExecutor: Executor? = null,
        missingToolsConversionStrategy: MissingToolsConversionStrategy =
            MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON),
        responseProcessor: ResponseProcessor? = null,
        serializer: JSONSerializer = JacksonSerializer()
    ) : this(prompt, model, maxAgentIterations, missingToolsConversionStrategy, responseProcessor, serializer) {
        strategyExecutor?.let { strategyDispatcher = it.asCoroutineDispatcher() }
        llmRequestExecutor?.let { llmRequestDispatcher = it.asCoroutineDispatcher() }
    }

    init {
        require(maxAgentIterations > 0) { "maxAgentIterations must be greater than 0" }
    }

    internal actual fun copy(
        prompt: Prompt,
        model: LLModel,
        maxAgentIterations: Int,
        missingToolsConversionStrategy: MissingToolsConversionStrategy,
        responseProcessor: ResponseProcessor?,
        serializer: JSONSerializer
    ): AIAgentConfig = AIAgentConfig(
        prompt = prompt,
        model = model,
        maxAgentIterations = maxAgentIterations,
        missingToolsConversionStrategy = missingToolsConversionStrategy,
        responseProcessor = responseProcessor,
        serializer = serializer
    ).also {
        it.strategyDispatcher = this.strategyDispatcher
        it.llmRequestDispatcher = this.llmRequestDispatcher
    }

    public actual companion object {
        public actual fun withSystemPrompt(
            prompt: String,
            llm: LLModel,
            id: String,
            maxAgentIterations: Int
        ): AIAgentConfig =
            AIAgentConfig(
                prompt = prompt(id) {
                    system(prompt)
                },
                model = llm,
                maxAgentIterations = maxAgentIterations
            )

        /**
         * Provides a builder for constructing instances of [AIAgentConfig].
         *
         * This method returns an instance of [InitialAIAgentBuilder], which allows the configuration of various
         * properties and dependencies required to build an [AIAgentConfig] instance.
         *
         * The builder pattern offers a flexible way to set optional parameters and ensures that mandatory
         * properties are properly initialized during the construction of the configuration object.
         *
         */
        @JavaAPI
        @JvmStatic
        public fun builder(): InitialAIAgentBuilder = InitialAIAgentBuilder()

        @JavaAPI
        public class InitialAIAgentBuilder {

            /**
             * Sets the Large Language Model (LLM) to be used for the agent's configuration.
             *
             * @param model The instance of [LLModel] that represents the desired language model,
             * including its provider, identifier, and capabilities.
             */
            public fun model(model: LLModel): AIAgentConfigBuilder = AIAgentConfigBuilder(
                model = model,
                strategyExecutor = null,
                llmRequestExecutor = null,
            )
        }

        /**
         * A builder class for constructing an instance of [AIAgentConfig] with customizable configuration options.
         */
        @JavaAPI
        public class AIAgentConfigBuilder(
            public val model: LLModel,
            public var prompt: Prompt? = null,
            public var maxAgentIterations: Int? = null,
            public var missingToolsConversionStrategy: MissingToolsConversionStrategy? = null,
            public var responseProcessor: ResponseProcessor? = null,
            internal var strategyExecutor: Executor? = null,
            internal var llmRequestExecutor: Executor? = null,
            internal var serializer: JSONSerializer = JacksonSerializer()
        ) {
            @Deprecated("Use constructor that accepts Executor instead of ExecutorService")
            @JavaAPI
            public constructor(
                model: LLModel,
                prompt: Prompt? = null,
                maxAgentIterations: Int? = null,
                missingToolsConversionStrategy: MissingToolsConversionStrategy? = null,
                responseProcessor: ResponseProcessor? = null,
                strategyExecutorService: ExecutorService? = null,
                llmRequestExecutorService: ExecutorService? = null,
                serializer: JSONSerializer = JacksonSerializer()
            ) : this(
                model = model,
                prompt = prompt,
                maxAgentIterations = maxAgentIterations,
                missingToolsConversionStrategy = missingToolsConversionStrategy,
                responseProcessor = responseProcessor,
                strategyExecutor = strategyExecutorService,
                llmRequestExecutor = llmRequestExecutorService,
                serializer = serializer
            )

            /**
             * Sets serializer for underlying tool calls and LLM requests
             *
             * @param serializer The JSON serializer to configure the AI agent with.
             * */
            public fun serializer(serializer: JSONSerializer): AIAgentConfigBuilder =
                apply { this.serializer = serializer }

            /**
             * Sets the prompt configuration for the AI agent.
             *
             * @param prompt The prompt to configure the AI agent with.
             */
            public fun prompt(prompt: Prompt): AIAgentConfigBuilder = apply { this.prompt = prompt }

            /**
             * Sets the maximum number of iterations allowed for the AI agent during its execution.
             *
             * @param maxAgentIterations The maximum number of iterations to be configured for the AI agent.
             */
            public fun maxAgentIterations(maxAgentIterations: Int): AIAgentConfigBuilder =
                apply { this.maxAgentIterations = maxAgentIterations }

            /**
             * Configures the strategy to handle missing tool definitions in prompts.
             *
             * @param strategy The strategy defining how missing tools in prompt history are to be converted
             *                 when sending prompts to the model.
             */
            public fun missingToolsConversionStrategy(strategy: MissingToolsConversionStrategy): AIAgentConfigBuilder =
                apply { this.missingToolsConversionStrategy = strategy }

            /**
             * Assigns a custom response processor to the configuration builder. The response processor is responsible for
             * processing and transforming the responses generated by the language model.
             *
             * @param processor an instance of [ResponseProcessor] to handle the processing of LLM responses, or null to remove the existing processor.
             */
            public fun responseProcessor(processor: ResponseProcessor?): AIAgentConfigBuilder =
                apply { this.responseProcessor = processor }

            /**
             * Sets the executor to be used for executing strategies within the agent configuration.
             *
             * @param executor The executor to manage the execution of strategies. Can be null.
             */
            public fun strategyExecutor(executor: Executor?): AIAgentConfigBuilder =
                apply { this.strategyExecutor = executor }

            /**
             * Sets the executor service to be used for executing strategies within the agent configuration.
             *
             * @param executor The executor service to manage the execution of strategies. Can be null.
             */
            @Deprecated("Use strategyExecutor instead", replaceWith = ReplaceWith("strategyExecutor(executor)"))
            public fun strategyExecutorService(executor: ExecutorService?): AIAgentConfigBuilder =
                strategyExecutor(executor)

            /**
             * Sets the executor for handling LLM requests.
             *
             * @param executor The executor to be used for executing LLM-related tasks.
             *                 If `null`, no specific executor will be set.
             */
            public fun llmRequestExecutor(executor: Executor?): AIAgentConfigBuilder =
                apply { this.llmRequestExecutor = executor }

            /**
             * Sets the executor service for handling LLM requests.
             *
             * @param executor The executor service to be used for executing LLM-related tasks.
             *                 If `null`, no specific executor will be set.
             */
            @Deprecated("Use llmRequestExecutor instead", replaceWith = ReplaceWith("llmRequestExecutor(executor)"))
            public fun llmRequestExecutorService(executor: ExecutorService?): AIAgentConfigBuilder =
                llmRequestExecutor(executor)

            /**
             * Constructs and returns an instance of [AIAgentConfig] using the values configured
             * in the builder. The method validates that all required fields are provided and assigns
             * default values to optional fields if they are not explicitly set.
             *
             */
            public fun build(): AIAgentConfig = AIAgentConfig(
                model = model,
                prompt = prompt ?: Prompt.Empty,
                maxAgentIterations = maxAgentIterations ?: 100,
                missingToolsConversionStrategy = missingToolsConversionStrategy
                    ?: MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON),
                responseProcessor = responseProcessor,
                strategyExecutor = strategyExecutor,
                llmRequestExecutor = llmRequestExecutor,
                serializer = serializer
            )
        }
    }
}
