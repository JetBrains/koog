package ai.koog.a2a.server.messages

import ai.koog.a2a.model.Message
import ai.koog.a2a.server.exceptions.MessageOperationException
import ai.koog.a2a.transport.ServerCallContext

/**
 * Storage of the messages of a context, e.g. an agent's conversation memory. Agents use it through
 * [ContextMessageStorage].
 *
 * Scoping: a client can send any context id, so storages may partition the messages by the [ServerCallContext] of
 * the call the agent acts for, e.g. by an owner in [ServerCallContext.state] and by [ServerCallContext.tenant].
 *
 * Implementations must ensure concurrency safety.
 */
public interface MessageStorage {
    /**
     * Saves [message] to its context.
     *
     * @param message the message to save
     * @param callContext the call the agent acts for
     * @throws MessageOperationException if the message can't be saved, e.g. it has no context id
     */
    public suspend fun save(message: Message, callContext: ServerCallContext)

    /**
     * Reads the messages of a context.
     *
     * @param contextId the context ID
     * @param callContext the call the agent acts for
     * @return the messages of [contextId] visible to [callContext], in the order they were saved
     */
    public suspend fun getByContext(contextId: String, callContext: ServerCallContext): List<Message>

    /**
     * Deletes the messages of [contextId] visible to [callContext].
     *
     * @param contextId the context ID
     * @param callContext the call the agent acts for
     * @throws MessageOperationException if the messages can't be deleted
     */
    public suspend fun deleteByContext(contextId: String, callContext: ServerCallContext)

    /**
     * Replaces the messages of [contextId] visible to [callContext] with [messages].
     *
     * @param contextId the context ID
     * @param messages the new messages
     * @param callContext the call the agent acts for
     * @throws MessageOperationException if the messages can't be replaced, e.g. one has another context id
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
     * @return the messages, in the order they were saved
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
