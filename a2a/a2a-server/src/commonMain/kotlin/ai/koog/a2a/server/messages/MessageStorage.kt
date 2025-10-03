package ai.koog.a2a.server.messages

import ai.koog.a2a.model.Message
import ai.koog.a2a.server.exceptions.MessageOperationException

/**
 * Storage interface for messages associated with a particular context.
 * Can be used to keep track of a conversation history.
 *
 * Implementations must ensure concurrency safety.
 */
public interface MessageStorage {
    /**
     * Saves a message to the storage.
     *
     * @param message the message to save
     * @throws MessageOperationException if the message cannot be saved
     */
    public suspend fun save(message: Message)

    /**
     * Retrieves all messages associated with the given context.
     *
     * @param contextId the context identifier
     */
    public suspend fun getByContext(contextId: String): List<Message>

    /**
     * Deletes all messages associated with the given context.
     *
     * @param contextId the context identifier
     * @throws MessageOperationException if some messages cannot be deleted
     */
    public suspend fun deleteByContext(contextId: String)

    /**
     * Replaces all messages associated with the given context.
     *
     * @param contextId the context identifier
     * @throws MessageOperationException if context cannot be replaced
     */
    public suspend fun replaceByContext(contextId: String, messages: List<Message>)
}

/**
 * Wrapper class around [MessageStorage] for interacting with a particular context.
 * Provides convenience methods and verification for context ID.
 *
 * @param contextId the context identifier
 * @param messageStorage the underlying [MessageStorage] implementation
 */
public class ContextMessageStorage(
    private val contextId: String,
    private val messageStorage: MessageStorage,
) {
    /**
     * Saves a message to the storage.
     *
     * @param message the message to save
     * @see [MessageStorage.save]
     */
    public suspend fun save(message: Message) {
        require(message.contextId == contextId) {
            "contextId of message must be same as current contextId"
        }

        messageStorage.save(message)
    }

    /**
     * Retrieves all messages associated with the current context.
     *
     * @see [MessageStorage.getByContext]
     */
    public suspend fun getAll(): List<Message> {
        return messageStorage.getByContext(contextId)
    }

    /**
     * Deletes all messages associated with the current context.
     *
     * @see [MessageStorage.deleteByContext]
     */
    public suspend fun deleteAll() {
        messageStorage.deleteByContext(contextId)
    }

    /**
     * Replaces all messages associated with the current context.
     *
     * @param messages the list of messages to replace
     * @see [MessageStorage.replaceByContext]
     */
    public suspend fun replaceAll(messages: List<Message>) {
        require(messages.all { it.contextId == contextId }) {
            "contextId of messages must be same as current contextId"
        }

        messageStorage.replaceByContext(contextId, messages)
    }
}
