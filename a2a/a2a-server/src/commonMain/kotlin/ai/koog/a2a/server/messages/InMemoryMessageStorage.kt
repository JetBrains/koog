package ai.koog.a2a.server.messages

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.model.Message
import ai.koog.a2a.server.exceptions.MessageOperationException
import ai.koog.a2a.utils.RWLock

/**
 * In-memory implementation of [MessageStorage] using a thread-safe map.
 *
 * This implementation stores messages in memory grouped by context ID and provides
 * concurrency safety through mutex locks.
 */
@OptIn(InternalA2AApi::class)
public class InMemoryMessageStorage : MessageStorage {
    private val messagesByContext = mutableMapOf<String, MutableList<Message>>()
    private val rwLock = RWLock()

    override suspend fun save(message: Message): Unit = rwLock.withWriteLock {
        val contextId = message.contextId
            ?: throw MessageOperationException("Message must have a contextId to be saved")

        messagesByContext.getOrPut(contextId) { mutableListOf() }.add(message)
    }

    override suspend fun getByContext(contextId: String): List<Message> = rwLock.withReadLock {
        messagesByContext[contextId]?.toList() ?: emptyList()
    }

    override suspend fun deleteByContext(contextId: String): Unit = rwLock.withReadLock {
        messagesByContext -= contextId
    }

    override suspend fun replaceByContext(contextId: String, messages: List<Message>): Unit = rwLock.withWriteLock {
        // Validate that all messages have the correct contextId
        val invalidMessages = messages.filter { it.contextId != contextId }
        if (invalidMessages.isNotEmpty()) {
            throw MessageOperationException(
                "All messages must have contextId '$contextId', but found messages with different contextIds: " +
                    invalidMessages.map { it.contextId }.distinct().joinToString()
            )
        }

        // Replace all messages for the context
        messagesByContext[contextId] = messages.toMutableList()
    }
}
