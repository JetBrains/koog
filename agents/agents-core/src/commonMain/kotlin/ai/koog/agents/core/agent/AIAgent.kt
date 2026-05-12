@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.utils.io.Closeable
import kotlin.jvm.JvmStatic

/**
 * Represents a basic interface for AI agent.
 */
public expect abstract class AIAgent<Input, Output>() : Closeable {

    /**
     * Represents the unique identifier for the AI agent.
     */
    public abstract val id: String

    /**
     * The configuration for the AI agent.
     */
    public abstract val agentConfig: AIAgentConfig

    /**
     * Executes the AI agent with the given input and retrieves the resulting output.
     *
     * @param agentInput The input for the agent.
     * @return The output produced by the agent.
     */
    public abstract suspend fun run(agentInput: Input, sessionId: String? = null): Output

    /**
     * Creates a new session for executing the agent with the given input.
     *
     * This method provides a way to get a session object that can be used to execute
     * the agent independently. The session manages the complete execution lifecycle, including
     * state tracking, pipeline coordination, and strategy execution.
     *
     * @return A session instance that can be used to run the agent with specific input and context.
     */
    public abstract fun createSession(sessionId: String? = null): AIAgentRunSession<Input, Output, out AIAgentContext>

    /**
     * Companion object with builder for [AIAgent]
     */
    public companion object {
        /**
         * Creates and returns a new instance of the [AIAgentBuilder] class to configure and construct an AI agent.
         *
         * @return An instance of [AIAgentBuilder] for configuring an AI agent.
         */
        @JvmStatic
        public fun builder(): AIAgentBuilder
    }
}
