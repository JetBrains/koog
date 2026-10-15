package ai.koog.a2a.server.session

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.TaskPushNotificationConfig
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Interface for generating unique IDs for new contexts, tasks and push notification configs.
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

    /**
     * Generates a unique push notification config ID for a config being created without one.
     *
     * Has a default implementation returning a random UUID, so existing [IdGenerator] implementations
     * keep working.
     *
     * @param config The push notification config (with a null or blank `id`) for which the ID is being generated.
     * @return A unique string representing the push notification config ID.
     */
    @OptIn(ExperimentalUuidApi::class)
    public suspend fun generatePushNotificationConfigId(config: TaskPushNotificationConfig): String =
        Uuid.random().toString()
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
