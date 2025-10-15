package ai.koog.agents.a2a.client.feature

import ai.koog.a2a.client.A2AClient
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
 * Agent feature that enables A2A client mode by providing access to registered A2A clients
 * from within agent strategies.
 *
 * This feature allows agents to communicate with other A2A-enabled agents by making
 * [A2AClient] instances available to agent nodes. When installed, it provides access to
 * a registry of A2A clients, allowing agent nodes to:
 * - Send messages to remote A2A agents
 * - Retrieve agent cards and capabilities
 * - Manage tasks on remote agents
 * - Subscribe to task events and streaming responses
 * - Configure push notifications
 *
 * The feature provides convenience nodes for common A2A client
 * operations like sending messages, retrieving tasks, and managing subscriptions.
 *
 * @property a2aClients Map of A2A clients keyed by agent ID
 *
 * @see ai.koog.a2a.client.A2AClient
 */
public class A2AAgentClient(
    public val a2aClients: Map<String, A2AClient>
) {
    /**
     * Configuration for the [A2AAgentClient] feature.
     */
    public class Config : FeatureConfig() {
        /**
         * Map of [A2AClient] instances keyed by agent ID for accessing remote A2A agents.
         */
        public var a2aClients: Map<String, A2AClient> = mapOf()
    }

    public companion object Feature :
        AIAgentGraphFeature<Config, A2AAgentClient>,
        AIAgentNonGraphFeature<Config, A2AAgentClient> {

        override val key: AIAgentStorageKey<A2AAgentClient> =
            createStorageKey<A2AAgentClient>("agents-features-a2a-client")

        override fun createInitialConfig(): Config = Config()

        override fun install(
            config: Config,
            pipeline: AIAgentGraphPipeline
        ) {
            pipeline.interceptContextAgentFeature(this) { _ ->
                A2AAgentClient(config.a2aClients)
            }
        }

        override fun install(
            config: Config,
            pipeline: AIAgentNonGraphPipeline
        ) {
            pipeline.interceptContextAgentFeature(this) {
                A2AAgentClient(config.a2aClients)
            }
        }
    }
}

/**
 * Retrieves the [A2AAgentClient] feature from the agent context.
 *
 * @return The installed A2AAgentClient feature
 * @throws IllegalStateException if the feature is not installed
 */
public fun AIAgentContext.a2aAgentClient(): A2AAgentClient = featureOrThrow(A2AAgentClient.Feature)

/**
 * Executes an action with the [A2AAgentClient] feature as the receiver.
 * This is a convenience function that retrieves the feature and provides it as the receiver for the action block.
 *
 * @param action The action to execute with A2AAgentClient as receiver
 * @return The result of the action
 * @throws IllegalStateException if the feature is not installed
 */
@OptIn(ExperimentalContracts::class)
public inline fun <T> AIAgentContext.withA2AAgentClient(action: A2AAgentClient.() -> T): T {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }

    return a2aAgentClient().action()
}

/**
 * Retrieves an A2A client by agent ID or throws if not found.
 *
 * @param agentId The identifier of the A2A agent to retrieve
 * @return The A2AClient instance for the specified agent ID
 * @throws NoSuchElementException if no client is registered with the given agent ID
 */
public fun A2AAgentClient.a2aClientOrThrow(agentId: String): A2AClient =
    a2aClients[agentId] ?: throw NoSuchElementException("A2A agent with id $agentId not found in the current agent context. Make sure to register it in the A2AAgentClient feature.")
