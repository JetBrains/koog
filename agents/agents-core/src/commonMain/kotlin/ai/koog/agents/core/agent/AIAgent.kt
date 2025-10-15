package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgent.Companion.State.Finished
import ai.koog.agents.core.agent.AIAgent.Companion.State.Running
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.utils.io.Closeable
import kotlinx.datetime.Clock
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi

/**
 * Represents a basic interface for AI agent.
 */
public interface AIAgent<Input, Output> : Closeable {

    /**
     * Represents the unique identifier for the AI agent.
     */
    public val id: String

    /**
     * The configuration for the AI agent.
     */
    public val agentConfig: AIAgentConfigBase

    /**
     * Retrieves the current state of the AI agent during its lifecycle.
     *
     * This method provides the current `State` of the agent, which can
     * be one of the defined states: [State.NotStarted], [State.Running], [State.Finished], or [State.Failed].
     *
     * @return The current state of the AI agent.
     */
    public suspend fun getState(): State<Output>

    /**
     * Retrieves the result of the operation if the current state is `State.Finished`.
     * Throws an `IllegalStateException` if the operation is not in a finished state.
     *
     * @return The result of type `Output` when the operation is completed successfully.
     * @throws IllegalStateException if the operation's state is not `State.Finished`.
     */
    public suspend fun result(): Output = when (val state = getState()) {
        is Finished<Output> -> state.result
        else -> throw IllegalStateException("Output is not ready, agent's state is: $state")
    }

    /**
     * Executes the AI agent with the given input and retrieves the resulting output.
     *
     * @param agentInput The input for the agent.
     * @return The output produced by the agent.
     */
    public suspend fun run(agentInput: Input): Output

    /**
     * The companion object for the AIAgent class, providing functionality to instantiate an AI agent
     * with a flexible configuration, input/output types, and execution strategy.
     */
    public companion object {
        /**
         * Represents the state of an AI agent during its lifecycle.
         *
         * This sealed interface provides different states to reflect whether the agent
         * has not started, is currently running, has completed its task successfully with a result,
         * or has failed with an exception.
         */
        public sealed interface State<Output> {
            /**
             * Creates and returns a copy of the current state object.
             *
             * @return A new instance of `State<Output>` that is a copy of the current object.
             */
            public fun copy(): State<Output>

            /**
             * Represents a state that indicates an action or process has not yet started.
             *
             * This class is part of the `State` sealed interface and is used to define
             * a specific state where no progress, execution, or processing has occurred.
             */
            public class NotStarted<Output> : State<Output> {
                override fun copy(): State<Output> = NotStarted()
            }

            /**
             * Represents the starting state of an operation or process.
             *
             * This class is a specialization of the `State` class, indicating the initial
             * state prior to progression or change. It overrides the `copy` method to
             * return a new instance of the same starting state.
             *
             * @param Output The type of output associated with the state.
             */
            public class Starting<Output> : State<Output> {
                override fun copy(): State<Output> = Starting()
            }

            /**
             * Represents the `Running` state of an AI agent, indicating that the agent is actively executing its tasks.
             *
             * This state provides access to the root context of the agent via the `rootContext` property, allowing
             * interaction with the overall execution environment, configuration, and state management facilities.
             *
             * The `rootContext` is marked with the `@InternalAgentsApi` annotation, meaning its usage is intended for
             * internal agent-related implementations and may not maintain backwards compatibility.
             *
             * @property rootContext Provides access to the root context of the agent, facilitating operations
             *                       such as state management, feature retrieval, and context-based workflows.
             *                       This allows the agent to perform actions and manage its execution lifecycle within the given context.
             */
            public class Running<Output>(
                @property:InternalAgentsApi public val rootContext: AIAgentContext
            ) : State<Output> {
                @OptIn(InternalAgentsApi::class)
                override fun copy(): State<Output> = Running(rootContext)
            }

            /**
             * Represents the final state of a computation or process with its resulting output.
             *
             * @param Output The type of the result produced by the finished computation or process.
             * @property result The computed result of the finished process.
             */
            public class Finished<Output>(
                public val result: Output
            ) : State<Output> {
                override fun copy(): State<Output> = Finished(result)
            }

            /**
             * Represents a state indicating an operation has failed.
             *
             * @property exception The throwable that caused the failure.
             */
            public class Failed<Output>(
                public val exception: Throwable
            ) : State<Output> {
                override fun copy(): State<Output> = Failed(exception)
            }
        }

        /**
         * Creates an instance of an AI agent based on the provided configuration, input/output types,
         * and execution strategy.
         *
         * @param Input The type of the input the AI agent will process.
         * @param Output The type of the output the AI agent will produce.
         * @param promptExecutor The executor responsible for processing prompts and interacting with the language model.
         * @param agentConfig The configuration for the AI agent, including the prompt, model, and other parameters.
         * @param toolRegistry The registry of tools available for use by the agent. Defaults to an empty registry.
         * @param strategy The strategy for executing the AI agent's graph logic, including workflows and decision-making.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param clock The clock to be used for time-related operations. Defaults to the system clock.
         * @param installFeatures A lambda expression to install additional features in the agent's feature context. Defaults to an empty implementation.
         * @return An instance of an AI agent configured with the specified parameters and capable of executing its logic.
         */
        @OptIn(ExperimentalUuidApi::class)
        public inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<Input, Output>,
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            clock: Clock = Clock.System,
            noinline installFeatures: FeatureContext.() -> Unit = {},
        ): AIAgent<Input, Output> {
            return GraphAIAgent(
                inputType = typeOf<Input>(),
                outputType = typeOf<Output>(),
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                toolRegistry = toolRegistry,
                strategy = strategy,
                id = id,
                clock = clock,
                installFeatures = installFeatures
            )
        }

        /**
         * Operator function to create and invoke an AI agent with the given parameters.
         *
         * @param promptExecutor The executor responsible for running the prompt and generating outputs.
         * @param prompt The prompt to be processed by the AI agent.
         * @param agentConfig Configuration settings for the AI agent.
         * @param strategy The strategy to be used for the AI agent's execution graph. Defaults to a single-run strategy.
         * @param toolRegistry Registry of tools available for the AI agent to use. Defaults to an empty registry.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param installFeatures Lambda function for installing additional features into the feature context. Defaults to an empty lambda.
         * @return An instance of AIAgent configured with the graph strategy.
         */
        @OptIn(ExperimentalUuidApi::class)
        public operator fun invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<String, String> = singleRunStrategy(),
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            installFeatures: FeatureContext.() -> Unit = {},
        ): AIAgent<String, String> = GraphAIAgent(
            inputType = typeOf<String>(),
            outputType = typeOf<String>(),
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            strategy = strategy,
            id = id,
            clock = Clock.System,
            installFeatures = installFeatures
        )

        /**
         * Creates a functional AI agent with the provided configurations and execution strategy.
         *
         * @param Input The type of the input the AI agent will process.
         * @param Output The type of the output the AI agent will produce.
         * @param promptExecutor The executor responsible for running prompts against the language model.
         * @param agentConfig The configuration for the AI agent, including prompt setup, language model, and iteration limits.
         * @param toolRegistry The registry containing available tools for the AI agent. Defaults to an empty registry.
         * @param strategy The strategy for executing the agent's logic, including workflows and decision-making.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @return A `FunctionalAIAgent` instance configured with the provided parameters and execution strategy.
         */
        @OptIn(ExperimentalUuidApi::class)
        public operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentFunctionalStrategy<Input, Output>,
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            clock: Clock = Clock.System,
            installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit = {},
        ): FunctionalAIAgent<Input, Output> {
            return FunctionalAIAgent(
                id = id,
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                toolRegistry = toolRegistry,
                strategy = strategy,
                clock = clock,
                installFeatures = installFeatures
            )
        }

        /**
         * Construction of an AI agent with the specified configurations and parameters.
         *
         * @param promptExecutor The executor responsible for processing language model prompts.
         * @param llmModel The specific large language model to be used for the agent.
         * @param strategy The strategy that defines the agent's workflow, defaulting to the [singleRunStrategy].
         * @param toolRegistry The set of tools available for the agent, defaulting to an empty registry.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param systemPrompt The system-level prompt used as context for the agent, defaulting to an empty string.
         * @param temperature The randomness or creativity of the model's responses, with valid values ranging typically from 0.0 to 1.0. Defaults to 1.0.
         * @param numberOfChoices The number of response choices to be generated, defaulting to 1.
         * @param maxIterations The maximum number of iterations the agent is allowed to perform, defaulting to 50.
         * @param installFeatures A function to configure additional features into the agent during initialization. Defaults to an empty configuration.
         * @return An instance of [AIAgent] configured with the provided parameters.
         */
        @OptIn(ExperimentalUuidApi::class)
        public operator fun invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            strategy: AIAgentGraphStrategy<String, String> = singleRunStrategy(),
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            systemPrompt: String = "",
            temperature: Double = 1.0,
            numberOfChoices: Int = 1,
            maxIterations: Int = 50,
            installFeatures: FeatureContext.() -> Unit = {}
        ): AIAgent<String, String> = AIAgent(
            id = id,
            promptExecutor = promptExecutor,
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "chat",
                    params = LLMParams(
                        temperature = temperature,
                        numberOfChoices = numberOfChoices
                    )
                ) {
                    system(systemPrompt)
                },
                model = llmModel,
                maxAgentIterations = maxIterations,
            ),
            toolRegistry = toolRegistry,
            installFeatures = installFeatures
        )

        /**
         * Creates and configures an AI agent using the provided parameters.
         *
         * @param Input The input type for the AI agent.
         * @param Output The output type for the AI agent.
         * @param promptExecutor An instance of [PromptExecutor] responsible for executing prompts with the language model.
         * @param llmModel The language model [LLModel] to be used by the agent.
         * @param strategy The agent strategy [AIAgentGraphStrategy] defining how the agent processes inputs and outputs.
         * @param toolRegistry An optional [ToolRegistry] specifying the tools available to the agent for execution. Defaults to `[ToolRegistry.EMPTY]`.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param clock A `Clock` instance used for time-related operations. Defaults to `Clock.System`.
         * @param systemPrompt A string representing the system-level prompt for the agent. Defaults to an empty string.
         * @param temperature A double value controlling the randomness of the model's output. Defaults to `1.0`.
         * @param numberOfChoices The number of choices the model should generate per invocation. Defaults to `1`.
         * @param maxIterations The maximum number of iterations the agent can perform. Defaults to `50`.
         * @param installFeatures An extension function on `FeatureContext` to install custom features for the agent. Defaults to an empty lambda.
         * @return A configured [AIAgent] instance that can process inputs and generate outputs using the specified strategy and model.
         */
        @OptIn(ExperimentalUuidApi::class)
        public inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            strategy: AIAgentGraphStrategy<Input, Output>,
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            clock: Clock = Clock.System,
            systemPrompt: String = "",
            temperature: Double = 1.0,
            numberOfChoices: Int = 1,
            maxIterations: Int = 50,
            noinline installFeatures: FeatureContext.() -> Unit = {},
        ): AIAgent<Input, Output> {
            return AIAgent(
                id = id,
                promptExecutor = promptExecutor,
                strategy = strategy,
                agentConfig = AIAgentConfig(
                    prompt = prompt(
                        id = "chat",
                        params = LLMParams(
                            temperature = temperature,
                            numberOfChoices = numberOfChoices
                        )
                    ) {
                        system(systemPrompt)
                    },
                    model = llmModel,
                    maxAgentIterations = maxIterations,
                ),
                toolRegistry = toolRegistry,
                installFeatures = installFeatures
            )
        }

        /**
         * Creates an [FunctionalAIAgent] with the specified parameters to execute a strategy with the assistance of a tool registry,
         * configured language model, and associated features.
         *
         * @param promptExecutor The executor used to process prompts for the language model.
         * @param llmModel The language model configuration defining the underlying LLM instance and its behavior.
         * @param func The operational strategy for the AI agent, which determines how to handle the provided input.
         * @param toolRegistry Registry containing tools available to the agent for use during execution. Default is an empty registry.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param systemPrompt The system prompt that sets the initial context or instructions for the AI agent.
         * @param temperature The temperature setting for the language model, which adjusts the diversity of output. Default is 1.0.
         * @param numberOfChoices The number of response choices to generate when querying the language model. Default is 1.
         * @param maxIterations The maximum number of iterations the agent is allowed to perform during execution. Default is 50.
         * @param installFeatures A lambda to configure and install features in the agent's context.
         * @return An AI agent instance configured with the provided parameters and ready to execute the specified strategy.
         */
        public operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            strategy: AIAgentFunctionalStrategy<Input, Output>,
            id: String? = null,
            systemPrompt: String = "",
            temperature: Double = 1.0,
            numberOfChoices: Int = 1,
            maxIterations: Int = 50,
            installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit = {},
        ): AIAgent<Input, Output> = FunctionalAIAgent(
            promptExecutor = promptExecutor,
            agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "chat",
                    params = LLMParams(
                        temperature = temperature,
                        numberOfChoices = numberOfChoices
                    )
                ) {
                    system(systemPrompt)
                },
                model = llmModel,
                maxAgentIterations = maxIterations,
            ),
            installFeatures = installFeatures,
            toolRegistry = toolRegistry,
            strategy = strategy
        )
    }
}

/**
 * Checks whether the AI agent is currently in a running state.
 *
 * @return `true` if the AI agent's state is `Running`, otherwise `false`.
 */
public suspend fun AIAgent<*, *>.isRunning(): Boolean = this.getState() is Running

/**
 * Checks whether the AI agent has reached a finished state.
 *
 * @return true if the current state of the AI agent is of type `Finished`, false otherwise.
 */
public suspend fun AIAgent<*, *>.isFinished(): Boolean = this.getState() is Finished
