package ai.koog.agents.a2a.server.feature

import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.agents.a2a.core.A2AMessage
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import kotlinx.serialization.Serializable

/**
 * Creates a node that sends an A2A message back to the client.
 *
 * @param name Optional node name for debugging and tracing
 * @param saveToStorage If true, also saves the message to storage before sending
 * @return A node that sends the message and passes it through unchanged
 * @see ai.koog.a2a.server.session.SessionEventProcessor.sendMessage
 * @see ai.koog.a2a.server.messages.MessageStorage.save
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2ARespondMessage(
    name: String? = null,
    saveToStorage: Boolean = false,
): AIAgentNodeDelegate<A2AMessage, A2AMessage> =
    node(name) { message ->
        withA2AAgentServer {
            eventProcessor.sendMessage(message)
            if (saveToStorage) {
                context.messageStorage.save(message)
            }
        }

        message
    }

/**
 * Creates a node that sends a task event (status update, creation, etc.) to the client.
 *
 * @param name Optional node name for debugging and tracing
 * @return A node that sends the task event and passes it through unchanged
 * @see ai.koog.a2a.server.session.SessionEventProcessor.sendTaskEvent
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2ARespondTaskEvent(
    name: String? = null,
): AIAgentNodeDelegate<TaskEvent, TaskEvent> =
    node(name) { event ->
        withA2AAgentServer {
            eventProcessor.sendTaskEvent(event)
        }

        event
    }

/**
 * Creates a node that saves a message to storage without sending it to the client.
 *
 * @param name Optional node name for debugging and tracing
 * @return A node that saves the message and passes it through unchanged
 * @see ai.koog.a2a.server.messages.MessageStorage.save
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AMessageStorageSave(
    name: String? = null,
): AIAgentNodeDelegate<A2AMessage, A2AMessage> =
    node(name) { event ->
        withA2AAgentServer {
            context.messageStorage.save(event)
        }

        event
    }

/**
 * Creates a node that loads all messages from storage for the current context.
 *
 * @param name Optional node name for debugging and tracing
 * @return A node that returns the list of all stored messages
 * @see ai.koog.a2a.server.messages.MessageStorage.getByContext
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AMessageStorageLoad(
    name: String? = null,
): AIAgentNodeDelegate<Unit, List<A2AMessage>> =
    node(name) {
        withA2AAgentServer {
            context.messageStorage.getAll()
        }
    }

/**
 * Creates a node that replaces all messages in storage for the current context.
 *
 * @param name Optional node name for debugging and tracing
 * @return A node that replaces all stored messages with the provided list
 * @see ai.koog.a2a.server.messages.MessageStorage.replaceByContext
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AMessageStorageReplace(
    name: String? = null,
): AIAgentNodeDelegate<List<A2AMessage>, Unit> =
    node(name) {
        withA2AAgentServer {
            context.messageStorage.replaceAll(it)
        }
    }

/**
 * Creates a node that loads all messages from storage for the current context.
 *
 * This is an alias for [nodeA2AMessageStorageLoad].
 *
 * @param name Optional node name for debugging and tracing
 * @return A node that returns the list of all stored messages
 * @see ai.koog.a2a.server.messages.MessageStorage.getByContext
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2AMessagesContextLoad(
    name: String? = null,
): AIAgentNodeDelegate<Unit, List<A2AMessage>> =
    node(name) {
        withA2AAgentServer {
            context.messageStorage.getAll()
        }
    }

/**
 * Parameters for retrieving a single task from storage.
 *
 * @property taskId The unique task identifier
 * @property historyLength Maximum number of messages to include in conversation history. Set to `null` for all messages
 * @property includeArtifacts Whether to include task artifacts in the response
 */
@Serializable
public data class A2ATaskGetParams(
    val taskId: String,
    val historyLength: Int? = 0,
    val includeArtifacts: Boolean = false,
)

/**
 * Creates a node that retrieves a single task by ID from storage.
 *
 * @param name Optional node name for debugging and tracing
 * @return A node that returns the task or null if not found
 * @see ai.koog.a2a.server.tasks.TaskStorage.get
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2ATaskGet(
    name: String? = null,
): AIAgentNodeDelegate<A2ATaskGetParams, Task?> =
    node(name) { params ->
        withA2AAgentServer {
            context.taskStorage.get(
                taskId = params.taskId,
                historyLength = params.historyLength,
                includeArtifacts = params.includeArtifacts
            )
        }
    }

/**
 * Parameters for retrieving multiple tasks from storage.
 *
 * @property taskIds List of task identifiers to retrieve
 * @property historyLength Maximum number of messages to include in conversation history. Set to `null` for all messages
 * @property includeArtifacts Whether to include task artifacts in the response
 */
@Serializable
public data class A2ATaskGetAllParams(
    val taskIds: List<String>,
    val historyLength: Int? = 0,
    val includeArtifacts: Boolean = false,
)

/**
 * Creates a node that retrieves multiple tasks by their IDs from storage.
 *
 * @param name Optional node name for debugging and tracing
 * @return A node that returns the list of found tasks (may be fewer than requested)
 * @see ai.koog.a2a.server.tasks.TaskStorage.getAll
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeA2ATaskGetAll(
    name: String? = null,
): AIAgentNodeDelegate<A2ATaskGetAllParams, List<Task>> =
    node(name) { params ->
        withA2AAgentServer {
            context.taskStorage.getAll(
                taskIds = params.taskIds,
                historyLength = params.historyLength,
                includeArtifacts = params.includeArtifacts
            )
        }
    }
