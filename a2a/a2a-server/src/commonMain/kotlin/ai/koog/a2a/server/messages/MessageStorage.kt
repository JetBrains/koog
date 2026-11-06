package ai.koog.a2a.server.messages

import ai.koog.a2a.model.Message
import ai.koog.a2a.server.exceptions.MessageOperationException
import ai.koog.a2a.transport.ServerCallContext

/**
 * Storage interface for messages associated with a particular context.
 * Can be used to keep track of a conversation history.
 *
 * Every method takes the [ServerCallContext] of the call the agent acts for, last. A client can send any context id,
 * so an owner-aware storage partitions the messages by it (e.g. by an owner in [ServerCallContext.state], and by
 * [ServerCallContext.tenant]). The in-memory implementation ignores it.
 *
 * Implementations must ensure concurrency safety.
 */
public interface MessageStorage {
    /**
     * Saves a message to the storage.
     *
     * @param message the message to save
     * @param callContext the call the agent acts for
     * @throws MessageOperationException if the message cannot be saved
     */
    public suspend fun save(message: Message, callContext: ServerCallContext)

    /**
     * Retrieves all messages associated with the given context.
     context*
     * @param contextId the context identifier
     * @param callContext the call the agent acts for
     */
    public suspend fun getByContext(contextId: String, callContext: ServerCallContext): List<Message>

    /**
     * Deletes all messages associated with the given context.
     *
     * @param contextId the context identifier
     * @param callContext the call the agent acts for
     * @throws MessageOperationException if some messages cannot be deleted
     */
    public suspend fun deleteByContext(contextId: String, callContext: ServerCallContext)

    /**
     * Replaces all messages associated with the given context.
     *
     * @param contextId the context identifier
     * @param messages the messages to store instead
     * @param callContext the call the agent acts for
     * @throws MessageOperationException if context cannot be replaced
     */
    public suspend fun replaceByContext(contextId: String, messages: List<Message>, callContext: ServerCallContext)
}

/**
 * Wrapper class around [MessageStorage] for interacting with a particular context.
 * Provides convenience methods and verification for context ID, and passes [callContext] to every [MessageStorage]
 * call.
 *
 * @param contextId the context identifier
 * @param callContext the call the agent acts for, see [MessageStorage]
 * @param messageStorage the underlying [MessageStorage] implementation
 */
public class ContextMessageStorage(
    private val contextId: String,
    private val callContext: ServerCallContext,
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

        messageStorage.save(message, callContext)
    }

    /**
     * Retrieves all messages associated with the current context.
     *
     * @see [MessageStorage.getByContext]
     */
    public suspend fun getAll(): List<Message> {
        return messageStorage.getByContext(contextId, callContext)
    }

    /**
     * Deletes all messages associated with the current context.
     *
     * @see [MessageStorage.deleteByContext]
     */
    public suspend fun deleteAll() {
        messageStorage.deleteByContext(contextId, callContext)
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

        messageStorage.replaceByContext(contextId, messages, callContext)
    }
}
