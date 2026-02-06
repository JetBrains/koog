package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi

/**
 * Represents the state of an AI agent during its lifecycle.
 *
 * This sealed interface provides different states to reflect whether the agent
 * has not started, is currently running, has completed its task successfully with a result,
 * or has failed with an exception.
 */
public sealed interface AIAgentState<Output> {
    /**
     * Creates and returns a copy of the current state object.
     *
     * @return A new instance of `AIAgentState<Output>` that is a copy of the current object.
     */
    public fun copy(): AIAgentState<Output>

    /**
     * Represents a state that indicates an action or process has not yet started.
     *
     * This class is part of the `State` sealed interface and is used to define
     * a specific state where no progress, execution, or processing has occurred.
     */
    public class NotStarted<Output> : AIAgentState<Output> {
        override fun copy(): AIAgentState<Output> = NotStarted()
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
    public class Starting<Output> : AIAgentState<Output> {
        override fun copy(): AIAgentState<Output> = Starting()
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
    ) : AIAgentState<Output> {
        @OptIn(InternalAgentsApi::class)
        override fun copy(): AIAgentState<Output> = Running(rootContext)
    }

    /**
     * Represents the final state of a computation or process with its resulting output.
     *
     * @param Output The type of the result produced by the finished computation or process.
     * @property result The computed result of the finished process.
     */
    public class Finished<Output>(
        public val result: Output
    ) : AIAgentState<Output> {
        override fun copy(): AIAgentState<Output> = Finished(result)
    }

    /**
     * Represents a state indicating an operation has failed.
     *
     * @property exception The throwable that caused the failure.
     */
    public class Failed<Output>(
        public val exception: Throwable
    ) : AIAgentState<Output> {
        override fun copy(): AIAgentState<Output> = Failed(exception)
    }
}
