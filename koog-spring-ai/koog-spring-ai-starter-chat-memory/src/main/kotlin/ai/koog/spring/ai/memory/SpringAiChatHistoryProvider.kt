package ai.koog.spring.ai.memory

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.messages.Message as SpringMessage

/**
 * A **conversation text memory** bridge between a Spring AI [ChatMemoryRepository] and a Koog [ChatHistoryProvider].
 *
 * This adapter targets repositories that persist only Spring AI `messageType` and `text`
 * (e.g., `JdbcChatMemoryRepository`). It is **not** a lossless Koog persistence adapter —
 * only plain-text conversational messages survive the round-trip.
 *
 * ### Persistable message types (text-only)
 * - [Message.System]
 * - [Message.User] (without attachments)
 * - [Message.Assistant] (without attachments)
 *
 * ### Silently dropped on store
 * The following Koog message types are non-persistable transport/runtime events and are
 * filtered out before delegation to the underlying [ChatMemoryRepository]:
 * - [Message.Tool.Call]
 * - [Message.Tool.Result]
 * - [Message.Reasoning]
 * - Any message carrying attachments (images, audio, video, files)
 *
 * ### Silently skipped on load
 * Spring AI `TOOL` rows (e.g., from JDBC repositories that previously stored tool traffic)
 * are skipped rather than causing an error, since they cannot be meaningfully mapped back
 * to Koog messages.
 *
 * Metadata fields such as timestamps, token counts, finish reasons, and custom metadata
 * are **not** preserved through the round-trip.
 *
 * @param repository the Spring AI [ChatMemoryRepository] to delegate to
 * @param dispatcher the [CoroutineDispatcher] used for blocking repository calls
 */
public class SpringAiChatHistoryProvider(
    internal val repository: ChatMemoryRepository,
    private val dispatcher: CoroutineDispatcher,
) : ChatHistoryProvider {

    private val logger = LoggerFactory.getLogger(SpringAiChatHistoryProvider::class.java)

    override suspend fun store(conversationId: String, messages: List<Message>) {
        val springMessages = messages.mapNotNull { filterAndConvert(it) }
        withContext(dispatcher) {
            repository.saveAll(conversationId, springMessages)
        }
    }

    override suspend fun load(conversationId: String): List<Message> {
        val springMessages = withContext(dispatcher) {
            repository.findByConversationId(conversationId)
        }
        return springMessages.mapNotNull { springMessageToKoogMessage(it) }
    }

    /**
     * Returns a Spring AI message for persistable Koog messages, or `null` for
     * non-persistable types (tool calls/results, reasoning, attachments).
     */
    private fun filterAndConvert(message: Message): SpringMessage? {
        if (message.hasAttachments()) {
            logger.debug(
                "Dropping Koog message with attachments (type={}); not persistable via Spring AI ChatMemoryRepository",
                message::class.simpleName
            )
            return null
        }
        return when (message) {
            is Message.System -> SystemMessage(message.content)
            is Message.User -> UserMessage(message.content)
            is Message.Assistant -> AssistantMessage(message.content)
            is Message.Tool.Call,
            is Message.Tool.Result,
            is Message.Reasoning -> {
                logger.debug(
                    "Dropping non-persistable Koog message (type={}); only System, User, and Assistant text messages are stored",
                    message::class.simpleName
                )
                null
            }
        }
    }

    /**
     * Converts a Spring AI [SpringMessage] to a Koog [Message].
     *
     * Only SYSTEM, USER, and ASSISTANT message types are mapped.
     * TOOL messages are skipped (returns `null`) because typical repositories
     * (e.g., JDBC) cannot round-trip them with usable payload.
     */
    private fun springMessageToKoogMessage(springMessage: SpringMessage): Message? {
        return when (springMessage.messageType) {
            MessageType.SYSTEM -> Message.System(springMessage.text ?: "", RequestMetaInfo.Empty)
            MessageType.USER -> Message.User(springMessage.text ?: "", RequestMetaInfo.Empty)
            MessageType.ASSISTANT -> Message.Assistant(springMessage.text ?: "", ResponseMetaInfo.Empty)
            MessageType.TOOL -> {
                logger.debug(
                    "Skipping Spring AI TOOL message on load; not mappable to a Koog message type"
                )
                null
            }
        }
    }
}
