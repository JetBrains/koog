package ai.koog.a2a.server.session

import ai.koog.a2a.model.Message
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Interface for generating unique IDs for new contexts and tasks.
 *
 * Called by the server to provide unique IDs when messages lack contextId or taskId,
 * preventing race conditions during concurrent agent execution. These generated IDs
 * are enforced when agents reply in newly created contexts or create tasks based on
 * incoming messages without existing task IDs.
 */
public interface IdGenerator {
    /**
     * Generates a unique context ID based on the given message.
     *
     * @param message The message for which the context ID is being generated.
     * @return A unique string representing the context ID.
     */
    public suspend fun generateContextId(message: Message): String

    /**
     * Generates a unique task ID based on the given message.
     *
     * @param message The message for which the task ID is being generated.
     * @return A unique string representing the task ID.
     */
    public suspend fun generateTaskId(message: Message): String
}

/**
 * Implementation of the [IdGenerator] interface that generates unique identifiers using UUIDs.
 *
 * This generator ensures that each context ID and task ID is uniquely identified, leveraging UUIDs
 * for randomness and collision resistance. IDs are generated only if the relevant existing ID
 * (contextId or taskId) is null in the incoming message.
 */
@OptIn(ExperimentalUuidApi::class)
public object UuidIdGenerator : IdGenerator {
    override suspend fun generateContextId(message: Message): String {
        require(message.contextId == null) {
            "Can't generate contextId for message with existing contextId"
        }

        return Uuid.random().toString()
    }

    override suspend fun generateTaskId(message: Message): String {
        require(message.taskId == null) {
            "Can't generate taskId for message with existing taskId"
        }

        return Uuid.random().toString()
    }
}
