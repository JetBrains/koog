package ai.koog.agents.a2a.server.feature

import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.AIAgentNonGraphFeature
import ai.koog.agents.core.feature.AIAgentNonGraphPipeline
import ai.koog.agents.core.feature.config.FeatureConfig
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Agent feature that enables A2A server mode by providing access to the request context and event processor
 * from within agent strategies.
 *
 * This feature is designed to be used within agents that are hosted as A2A servers via [ai.koog.a2a.server.agent.AgentExecutor].
 * When installed, it makes the [context] and [eventProcessor] available to agent nodes, allowing them to:
 * - Access incoming A2A messages and task information
 * - Send outgoing messages and task events back to the client
 * - Interact with message and task storage
 * - Manage the A2A session lifecycle
 *
 * The feature provides convenience nodes for common A2A operations like
 * sending messages, updating task status, and accessing storage.
 *
 * @property context The A2A [RequestContext] from [ai.koog.a2a.server.agent.AgentExecutor.execute]
 * @property eventProcessor The A2A [SessionEventProcessor] from [ai.koog.a2a.server.agent.AgentExecutor.execute]
 *
 * @see ai.koog.a2a.server.agent.AgentExecutor
 * @see ai.koog.a2a.server.session.RequestContext
 * @see ai.koog.a2a.server.session.SessionEventProcessor
 */
public class A2AAgentServer(
    public val context: RequestContext<MessageSendParams>,
    public val eventProcessor: SessionEventProcessor
) {
    /**
     * Configuration for the [A2AAgentServer] feature.
     */
    public class Config : FeatureConfig() {
        /**
         * The A2A [RequestContext] from [ai.koog.a2a.server.agent.AgentExecutor.execute]
         * @see RequestContext
         */
        public lateinit var context: RequestContext<MessageSendParams>

        /**
         * The A2A [SessionEventProcessor] from [ai.koog.a2a.server.agent.AgentExecutor.execute]
         * @see SessionEventProcessor
         */
        public lateinit var eventProcessor: SessionEventProcessor
    }

    public companion object Feature :
        AIAgentGraphFeature<Config, A2AAgentServer>,
        AIAgentNonGraphFeature<Config, A2AAgentServer> {

        override val key: AIAgentStorageKey<A2AAgentServer> =
            createStorageKey<A2AAgentServer>("agents-features-a2a-server")

        override fun createInitialConfig(): Config = Config()

        override fun install(
            config: Config,
            pipeline: AIAgentGraphPipeline
        ) {
            pipeline.interceptContextAgentFeature(this) { _ ->
                A2AAgentServer(config.context, config.eventProcessor)
            }
        }

        override fun install(
            config: Config,
            pipeline: AIAgentNonGraphPipeline
        ) {
            pipeline.interceptContextAgentFeature(this) {
                A2AAgentServer(config.context, config.eventProcessor)
            }
        }
    }
}

/**
 * Retrieves the [A2AAgentServer] feature from the agent context.
 *
 * @return The installed A2AAgentExecutor feature
 * @throws IllegalStateException if the feature is not installed
 */
public fun AIAgentContext.a2aAgentServer(): A2AAgentServer = featureOrThrow(A2AAgentServer.Feature)

/**
 * Executes an action with the [A2AAgentServer] feature as the receiver.
 * This is a convenience function that retrieves the feature and provides it as the receiver for the action block.
 *
 * @param action The action to execute with A2AAgentExecutor as receiver
 * @return The result of the action
 * @throws IllegalStateException if the feature is not installed
 */
@OptIn(ExperimentalContracts::class)
public inline fun <T> AIAgentContext.withA2AAgentServer(action: A2AAgentServer.() -> T): T {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }

    return a2aAgentServer().action()
}
