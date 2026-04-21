package ai.koog.agents.chatMemory.feature

import ai.koog.prompt.message.Message
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A simple in-memory implementation of [ChatHistoryProvider].
 *
 * Stores conversation histories in a map keyed by conversation ID.
 * Thread-safe via a coroutine [Mutex].
 * Useful for testing, prototyping, and short-lived applications where
 * persistence across process restarts is not required.
 */
public class InMemoryChatHistoryProvider : ChatHistoryProvider {
    private val mutex = Mutex()
    private val histories: MutableMap<String, List<Message>> = mutableMapOf()

    override suspend fun store(conversationId: String, messages: List<Message>) {
        mutex.withLock {
            histories[conversationId] = messages
        }
    }

    override suspend fun load(conversationId: String): List<Message> {
        return mutex.withLock {
            histories[conversationId] ?: emptyList()
        }
    }
}
