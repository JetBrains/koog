package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment

/**
 * Feature implementation for agent and strategy interception.
 *
 * @param TFeature The type of feature
 * @property feature The feature instance
 */
public class AgentHandler<TFeature : Any>(public val feature: TFeature) {

    /**
     * Configurable transformer used to manipulate or modify an instance of AgentEnvironment.
     * Allows customization of the environment during agent creation or updates by applying
     * the provided transformation logic.
     */
    public var environmentTransformer: AgentEnvironmentTransformer<TFeature> =
        AgentEnvironmentTransformer { _, it -> it }

    /**
     * A handler invoked before an agent is started. This can be used to perform custom logic
     * such as initialization or validation before the agent begins execution.
     *
     * The handler is triggered with the context of the agent start process.
     * It is intended to allow for feature-specific setup or preparation.
     */
    public var beforeAgentStartedHandler: BeforeAgentStartedHandler<TFeature> =
        BeforeAgentStartedHandler { _ -> }

    /**
     * Defines a handler invoked when an agent execution is completed.
     * This handler processes the outcome of the agent's operation, allowing
     * for custom behavior upon completion.
     *
     * The `AgentFinishedHandler` functional interface is used for this purpose,
     * providing a suspendable function that takes the strategy name and an
     * optional result of the execution.
     */
    public var agentFinishedHandler: AgentFinishedHandler =
        AgentFinishedHandler { _ -> }

    /**
     * A handler invoked when an error occurs during an agent's execution.
     * This handler allows custom logic to be executed in response to execution errors.
     */
    public var agentRunErrorHandler: AgentRunErrorHandler =
        AgentRunErrorHandler { _ -> }

    /**
     * A handler that is triggered before an agent is closed.
     *
     * This variable represents an implementation of the `AgentBeforeCloseHandler` functional interface,
     * which defines the behavior to execute in the pre-closing phase of an agent's lifecycle. It receives
     * the necessary context encapsulating specific details related to the agent and its closing event.
     *
     * Use this handler to implement custom logic, resource cleanup, or other operations that should occur
     * before the agent is terminated or closed.
     */
    public var agentBeforeCloseHandler: AgentBeforeCloseHandler =
        AgentBeforeCloseHandler { _ -> }

    /**
     * Transforms the provided AgentEnvironment using the configured environment transformer.
     *
     * @param environment The AgentEnvironment to be transformed
     */
    public fun transformEnvironment(
        context: AgentTransformEnvironmentContext<TFeature>,
        environment: AIAgentEnvironment
    ): AIAgentEnvironment =
        environmentTransformer.transform(context, environment)

    /**
     * Transforms the provided AgentEnvironment using the configured environment transformer.
     *
     * @param environment The AgentEnvironment to be transformed
     */
    @Suppress("UNCHECKED_CAST")
    internal fun transformEnvironmentUnsafe(context: AgentTransformEnvironmentContext<*>, environment: AIAgentEnvironment) =
        transformEnvironment(context as AgentTransformEnvironmentContext<TFeature>, environment)

    /**
     * Handles the logic to be executed before an agent starts.
     *
     * @param context The context containing necessary information about the agent,
     *                strategy, and feature to be processed before the agent starts.
     */
    public suspend fun handleBeforeAgentStarted(context: AgentStartContext<TFeature>) {
        beforeAgentStartedHandler.handle(context)
    }

    /**
     * Handles preliminary processes required before an agent is started, using an unsafe context cast.
     *
     * @param eventContext The agent start context containing information about the agent,
     *                strategy, and associated feature. The context is cast unsafely to
     *                the expected generic type.
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public suspend fun handleBeforeAgentStartedUnsafe(eventContext: AgentStartContext<*>) {
        handleBeforeAgentStarted(eventContext as AgentStartContext<TFeature>)
    }
}

/**
 * Handler for transforming an instance of AgentEnvironment.
 *
 * Ex: useful for mocks in tests
 *
 * @param TFeature The type of the feature associated with the agent.
 */
public fun interface AgentEnvironmentTransformer<TFeature : Any> {
    /**
     * Transforms the provided agent environment based on the given context.
     *
     * @param context The context containing the agent, strategy, and feature information
     * @param environment The current agent environment to be transformed
     * @return The transformed agent environment
     */
    public fun transform(context: AgentTransformEnvironmentContext<TFeature>, environment: AIAgentEnvironment): AIAgentEnvironment
}

/**
 * Handler for creating a feature instance in a stage context.
 *
 * @param FeatureT The type of feature being handled
 */
public fun interface AgentContextHandler<FeatureT : Any> {
    /**
     * Creates a feature instance for the given stage context.
     *
     * @param context The stage context where the feature will be used
     * @return A new instance of the feature
     */
    public fun handle(context: AIAgentContextBase): FeatureT
}

/**
 * Functional interface to define a handler that is invoked before an agent starts.
 * This handler allows custom pre-start logic to be executed with access to the
 * provided agent start context and associated feature.
 *
 * @param TFeature The type of the feature associated with the agent.
 */
public fun interface BeforeAgentStartedHandler<TFeature: Any> {
    /**
     * Handles operations to be performed before an agent is started.
     * Provides access to the context containing information about the agent's strategy, feature, and related configurations.
     *
     * @param context The context that encapsulates the agent, its strategy, and the associated feature
     */
    public suspend fun handle(context: AgentStartContext<TFeature>)
}

/**
 * Functional interface for handling the completion of an agent's operation.
 * This handler is executed when an agent has finished its work, and it provides the name
 * of the strategy that was executed along with an optional result.
 */
public fun interface AgentFinishedHandler {
    /**
     * Handles the completion of an operation or process for the specified strategy.
     *
     * @param context The context containing information about the completed agent operation.
     */
    public suspend fun handle(context: AgentFinishedContext)
}

/**
 * Functional interface for handling errors that occur during the execution of an agent run.
 *
 * This handler provides a mechanism to process and respond to errors associated with a specific
 * strategy execution. It can be used to implement custom error-handling logic tailored to the
 * requirements of an agent or strategy.
 */
public fun interface AgentRunErrorHandler {
    /**
     * Handles an error that occurs during the execution of an agent's strategy.
     */
    public suspend fun handle(eventContext: AgentRunErrorContext)
}

/**
 * Functional interface for handling logic that needs to be executed
 * before an agent is closed.
 *
 * @param FeatureT The type of the feature associated with the context.
 * @property strategy The AI agent strategy that defines the workflow and execution logic for the AI agent.
 * @property agent The AI agent being managed or operated upon in the context.
 * @property feature An additional feature or configuration associated with the context.
 */
public class AgentCreateContext<FeatureT>(
    public val strategy: AIAgentStrategy<*, *>,
    public val agent: AIAgent<*, *>,
    public val feature: FeatureT
) {
    /**
     * Executes a given block of code with the `AIAgentStrategy` instance of this context.
     *
     * @param block A suspending lambda function that receives the `AIAgentStrategy` instance.
     */
    public suspend fun readStrategy(block: suspend (AIAgentStrategy<*, *>) -> Unit) {
        block(strategy)
    }
}

/**
 * Functional interface for handling logic that needs to be executed
 * before an agent is closed.
 *
 * This handler provides an opportunity to perform cleanup operations
 * or any necessary pre-termination processes based on the context
 * provided through `AgentBeforeCloseHandlerContext`.
 *
 * @see AgentBeforeCloseContext
 */
public fun interface AgentBeforeCloseHandler {
    /**
     * Handles an event that occurs before an agent is closed, allowing for any necessary
     * pre-termination or cleanup operations to be executed.
     *
     * @param eventContext The context of the agent that is about to be closed, containing
     *                     information such as the agent's identifier.
     */
    public suspend fun handle(eventContext: AgentBeforeCloseContext)
}
