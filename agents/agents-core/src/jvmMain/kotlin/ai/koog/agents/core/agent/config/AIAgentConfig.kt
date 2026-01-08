@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.config

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import java.util.concurrent.ExecutorService

public actual class AIAgentConfig actual constructor(
    public actual override val prompt: Prompt,
    public actual override val model: LLModel,
    public actual val maxAgentIterations: Int,
    public actual val missingToolsConversionStrategy: MissingToolsConversionStrategy,
    public actual val responseProcessor: ResponseProcessor?
) : AIAgentConfigBase {

    /**
     * [ExecutorService] for running agent strategy logic
     *
     * By default, all agent operations will be performed on [kotlinx.coroutines.Dispatchers.Default]
     *  */
    @JavaAPI
    @property:PublishedApi
    internal var strategyExecutorService: ExecutorService? = null

    /**
     * IO-bounded [ExecutorService] for performing LLM communications
     *
     * By default, all IO/LLM operations will be performed on [kotlinx.coroutines.Dispatchers.IO]
     * */
    @JavaAPI
    @property:PublishedApi
    internal var llmRequestExecutorService: ExecutorService? = null

    @JvmOverloads
    public constructor(
        prompt: Prompt,
        model: LLModel,
        maxAgentIterations: Int,
        agentStrategyExecutorService: ExecutorService?,
        llmRequestExecutorService: ExecutorService? = null,
        missingToolsConversionStrategy: MissingToolsConversionStrategy =
            MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON),
        responseProcessor: ResponseProcessor? = null
    ) : this(prompt, model, maxAgentIterations, missingToolsConversionStrategy, responseProcessor) {
        this.strategyExecutorService = agentStrategyExecutorService
        this.llmRequestExecutorService = llmRequestExecutorService
    }

    init {
        require(maxAgentIterations > 0) { "maxAgentIterations must be greater than 0" }
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
         * This method returns an instance of [AIAgentConfigBuilder], which allows the configuration of various
         * properties and dependencies required to build an [AIAgentConfig] instance.
         *
         * The builder pattern offers a flexible way to set optional parameters and ensures that mandatory
         * properties are properly initialized during the construction of the configuration object.
         *
         * @return an instance of [AIAgentConfigBuilder] to configure and build an [AIAgentConfig] object.
         */
        @JavaAPI
        @JvmStatic
        public fun builder(model: LLModel): AIAgentConfigBuilder = AIAgentConfigBuilder(model = model)

        /**
         * A builder class for constructing an instance of [AIAgentConfig] with customizable configuration options.
         */
        @JavaAPI
        public class AIAgentConfigBuilder(
            public var model: LLModel,
            public var prompt: Prompt? = null,
            public var maxAgentIterations: Int? = null,
            public var missingToolsConversionStrategy: MissingToolsConversionStrategy? = null,
            public var responseProcessor: ResponseProcessor? = null,
            internal var strategyExecutorService: ExecutorService? = null,
            internal var llmRequestExecutorService: ExecutorService? = null
        ) {
            /**
             * Sets the prompt configuration for the AI agent.
             *
             * @param prompt The prompt to configure the AI agent with.
             * @return The updated instance of [AIAgentConfigBuilder].
             */
            public fun prompt(prompt: Prompt): AIAgentConfigBuilder = apply { this.prompt = prompt }

            /**
             * Sets the Large Language Model (LLM) to be used for the agent's configuration.
             *
             * @param model The instance of [LLModel] that represents the desired language model,
             * including its provider, identifier, and capabilities.
             * @return The current instance of [AIAgentConfigBuilder] for method chaining.
             */
            public fun model(model: LLModel): AIAgentConfigBuilder = apply { this.model = model }

            /**
             * Sets the maximum number of iterations allowed for the AI agent during its execution.
             *
             * @param maxAgentIterations The maximum number of iterations to be configured for the AI agent.
             * @return The current instance of [AIAgentConfigBuilder] to allow for method chaining.
             */
            public fun maxAgentIterations(maxAgentIterations: Int): AIAgentConfigBuilder =
                apply { this.maxAgentIterations = maxAgentIterations }

            /**
             * Configures the strategy to handle missing tool definitions in prompts.
             *
             * @param strategy The strategy defining how missing tools in prompt history are to be converted
             *                 when sending prompts to the model.
             * @return The current instance of [AIAgentConfigBuilder] for method chaining.
             */
            public fun missingToolsConversionStrategy(strategy: MissingToolsConversionStrategy): AIAgentConfigBuilder =
                apply { this.missingToolsConversionStrategy = strategy }

            /**
             * Assigns a custom response processor to the configuration builder. The response processor is responsible for
             * processing and transforming the responses generated by the language model.
             *
             * @param processor an instance of [ResponseProcessor] to handle the processing of LLM responses, or null to remove the existing processor.
             * @return the updated instance of [AIAgentConfigBuilder].
             */
            public fun responseProcessor(processor: ResponseProcessor?): AIAgentConfigBuilder =
                apply { this.responseProcessor = processor }

            /**
             * Sets the executor service to be used for executing strategies within the agent configuration.
             *
             * @param executor The `ExecutorService` to manage the execution of strategies. Can be null.
             * @return The updated `AIAgentConfigBuilder` instance.
             */
            public fun strategyExecutorService(executor: ExecutorService?): AIAgentConfigBuilder =
                apply { this.strategyExecutorService = executor }

            /**
             * Sets the executor service for handling LLM (Language Learning Model) requests.
             *
             * @param executor The executor service to be used for executing LLM-related tasks.
             *                 If `null`, no specific executor will be set.
             * @return The current instance of [AIAgentConfigBuilder], allowing for method chaining.
             */
            public fun llmRequestExecutorService(executor: ExecutorService?): AIAgentConfigBuilder =
                apply { this.llmRequestExecutorService = executor }

            /**
             * Constructs and returns an instance of [AIAgentConfig] using the values configured
             * in the builder. The method validates that all required fields are provided and assigns
             * default values to optional fields if they are not explicitly set.
             *
             * @return a fully constructed and validated [AIAgentConfig] instance
             * @throws IllegalStateException if required fields such as prompt or model are null
             */
            public fun build(): AIAgentConfig = AIAgentConfig(
                model = model,
                prompt = prompt ?: Prompt.Empty,
                maxAgentIterations = maxAgentIterations ?: 100,
                missingToolsConversionStrategy = missingToolsConversionStrategy
                    ?: MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON),
                responseProcessor = responseProcessor,
                agentStrategyExecutorService = strategyExecutorService,
                llmRequestExecutorService = llmRequestExecutorService
            )
        }
    }
}
