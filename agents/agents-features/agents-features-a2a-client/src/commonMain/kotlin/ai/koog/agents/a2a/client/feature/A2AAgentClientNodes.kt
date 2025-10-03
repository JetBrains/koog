package ai.koog.agents.a2a.client.feature

import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.CommunicationEvent
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import ai.koog.a2a.transport.ClientCallContext
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.Response
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Request parameters for A2A client operations that require call context and typed parameters.
 *
 * @property agentId The identifier of the A2A agent to send the request to, with which it is registered in [A2AAgentClient].
 * @property callContext The [io.ktor.server.application.CallContext]
 * @property params The typed parameters for the specific A2A operation
 */
@Serializable
public data class A2AClientRequest<T>(
    val agentId: String,
    val callContext: ClientCallContext,
    val params: T
)

/**
 * Information about a registered A2A agent client.
 *
 * @property agentId The identifier with which the agent is registered in [A2AAgentClient]
 * @property agentCard The cached agent card for this agent
 */
public data class A2AClientAgentInfo(
    val agentId: String,
    val agentCard: AgentCard,
)

/**
 * Creates a node that retrieves information about all A2A agents registered in [A2AAgentClient].
 *
 * @param name Optional node name for debugging and tracing
 * @return A node that returns a list of all registered agents with their cached agent cards
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientGetAllAgents(
    name: String? = null,
): AIAgentNodeDelegate<Unit, List<A2AClientAgentInfo>> =
    node(name) {
        withA2AAgentClient {
            a2aClients.map { (agentId, a2aClient) ->
                A2AClientAgentInfo(
                    agentId = agentId,
                    agentCard = a2aClient.cachedAgentCard()
                )
            }
        }
    }

/**
 * Creates a node that retrieves an agent card from an A2A server.
 * Input is an agent id with which [ai.koog.a2a.client.A2AClient] is registered in [A2AAgentClient].
 *
 * @see ai.koog.a2a.client.A2AClient.getAgentCard
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientGetAgentCard(
    name: String? = null,
): AIAgentNodeDelegate<String, AgentCard> =
    node(name) { agentId ->
        withA2AAgentClient {
            a2aClientOrThrow(agentId).getAgentCard()
        }
    }

/**
 * Creates a node that retrieves the cached agent card without making a network call.
 * Input is an agent id with which [ai.koog.a2a.client.A2AClient] is registered in [A2AAgentClient].
 *
 * @see ai.koog.a2a.client.A2AClient.cachedAgentCard
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientCachedAgentCard(
    name: String? = null,
): AIAgentNodeDelegate<String, AgentCard> =
    node(name) { agentId ->
        withA2AAgentClient {
            a2aClientOrThrow(agentId).cachedAgentCard()
        }
    }

/**
 * Creates a node that retrieves an authenticated extended agent card.
 *
 * @see ai.koog.a2a.client.A2AClient.getAuthenticatedExtendedAgentCard
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientGetAuthenticatedExtendedAgentCard(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<Nothing?>, Response<AgentCard>> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .getAuthenticatedExtendedAgentCard(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
        }
    }

/**
 * Creates a node that sends a message to an A2A agent.
 *
 * @see ai.koog.a2a.client.A2AClient.sendMessage
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientSendMessage(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<MessageSendParams>, CommunicationEvent> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .sendMessage(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
                .data
        }
    }

/**
 * Creates a node that sends a message to an A2A agent with streaming response.
 *
 * @see ai.koog.a2a.client.A2AClient.sendMessageStreaming
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientSendMessageStreaming(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<MessageSendParams>, Flow<Response<Event>>> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .sendMessageStreaming(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
        }
    }

/**
 * Creates a node that retrieves a task by ID from an A2A agent.
 *
 * @see ai.koog.a2a.client.A2AClient.getTask
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientGetTask(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<TaskQueryParams>, Task> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .getTask(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
                .data
        }
    }

/**
 * Creates a node that cancels a task on an A2A agent.
 *
 * @see ai.koog.a2a.client.A2AClient.cancelTask
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientCancelTask(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<TaskIdParams>, Task> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .cancelTask(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
                .data
        }
    }

/**
 * Creates a node that resubscribes to task events from an A2A agent.
 *
 * @see ai.koog.a2a.client.A2AClient.resubscribeTask
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientResubscribeTask(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<TaskIdParams>, Flow<Response<Event>>> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .resubscribeTask(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
        }
    }

/**
 * Creates a node that sets push notification configuration for a task.
 *
 * @see ai.koog.a2a.client.A2AClient.setTaskPushNotificationConfig
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientSetTaskPushNotificationConfig(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<TaskPushNotificationConfig>, TaskPushNotificationConfig> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .setTaskPushNotificationConfig(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
                .data
        }
    }

/**
 * Creates a node that retrieves push notification configuration for a task.
 *
 * @see ai.koog.a2a.client.A2AClient.getTaskPushNotificationConfig
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientGetTaskPushNotificationConfig(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<TaskPushNotificationConfigParams>, TaskPushNotificationConfig> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .getTaskPushNotificationConfig(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
                .data
        }
    }

/**
 * Creates a node that lists all push notification configurations for a task.
 *
 * @see ai.koog.a2a.client.A2AClient.listTaskPushNotificationConfig
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientListTaskPushNotificationConfig(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<TaskIdParams>, List<TaskPushNotificationConfig>> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .listTaskPushNotificationConfig(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
                .data
        }
    }

/**
 * Creates a node that deletes push notification configuration for a task.
 *
 * @see ai.koog.a2a.client.A2AClient.deleteTaskPushNotificationConfig
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AClientDeleteTaskPushNotificationConfig(
    name: String? = null,
): AIAgentNodeDelegate<A2AClientRequest<TaskPushNotificationConfigParams>, Unit> =
    node(name) { request ->
        withA2AAgentClient {
            a2aClientOrThrow(request.agentId)
                .deleteTaskPushNotificationConfig(
                    request = Request(data = request.params),
                    ctx = request.callContext,
                )
        }
    }
