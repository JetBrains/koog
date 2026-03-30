package ai.koog.agents.features.chathistory.aws

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.Message
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient
import aws.sdk.kotlin.services.bedrockagentcore.model.CreateEventRequest
import aws.sdk.kotlin.services.bedrockagentcore.model.Event
import aws.sdk.kotlin.services.bedrockagentcore.model.ListEventsRequest
import aws.sdk.kotlin.services.bedrockagentcore.model.PayloadType
import aws.smithy.kotlin.runtime.SdkBaseException
import aws.smithy.kotlin.runtime.time.Instant
import org.slf4j.LoggerFactory

/**
 * A [ChatHistoryProvider] implementation backed by Amazon Bedrock AgentCore Memory.
 *
 * This provider stores and retrieves **conversational** history only ([Message.User] and
 * [Message.Assistant]) using the AgentCore `createEvent` and `listEvents` APIs.
 * Non-conversational message types (System, Tool, Reasoning, attachments) are intentionally
 * outside the scope of this provider and are silently filtered out.
 *
 * Key behaviors:
 * - **eventId-based delta tracking**: [store] filters incoming messages to conversational types
 *   and saves only those without an `agentcore.eventId` in metadata (i.e., new messages).
 *   Messages loaded via [load] carry eventId in their metadata, so when they flow back
 *   through the prompt and into [store], they are recognized as already persisted and skipped.
 * - **Full-history loading**: [load] fetches all events (paginated) and returns them
 *   in chronological order.
 * - **Loaded messages carry eventId**: Messages returned by [load] have the AgentCore
 *   eventId attached in their metadata, and use the event's original timestamp.
 * - **Configurable non-conversational handling**: controlled by [ignoreUnsupportedValues].
 * - **Plain text only**: Only conversational messages with plain-text content are supported.
 *   Messages with attachments or non-text parts are rejected or skipped based on [ignoreUnsupportedValues].
 *
 * @param client The Bedrock AgentCore client used for API calls.
 * @param memoryId The AgentCore memory identifier (must not be blank).
 * @param defaultSession Session ID used when conversationId has no session component.
 * @param pageSize Maximum number of events per page when listing events.
 * @param totalEventsLimit Optional cap on the total number of events to fetch during [load].
 * @param ignoreUnsupportedValues If `true`, non-conversational message/role types are silently skipped.
 *   If `false`, they cause an [IllegalStateException].
 * @throws AgentcoreMemoryException.ConfigurationException if [memoryId] is blank.
 */
public class AgentcoreChatHistoryProvider @JvmOverloads constructor(
    public val client: BedrockAgentCoreClient,
    public val memoryId: String,
    defaultSession: String = AgentcoreConversationIdParser.DEFAULT_SESSION,
    public val pageSize: Int = DEFAULT_PAGE_SIZE,
    public val totalEventsLimit: Int? = null,
    public val ignoreUnsupportedValues: Boolean = true
) : ChatHistoryProvider {

    private val conversationIdParser = AgentcoreConversationIdParser(defaultSession)

    private val logger = LoggerFactory.getLogger("AgentcoreChatHistoryProvider")

    init {
        if (memoryId.isBlank()) {
            throw AgentcoreMemoryException.ConfigurationException("memoryId cannot be null or empty")
        }
    }

    override suspend fun store(
        conversationId: String,
        messages: List<Message>
    ) {
        val (actorId, sessionId) = conversationIdParser.parse(conversationId)

        if (messages.isEmpty()) return

        // Delta detection: only save messages without eventId (new messages).
        // Messages loaded via load() carry eventId in metadata and are skipped.
        val deltaPayloads = messages
            .filter { AgentcoreMessageConverter.getEventId(it) == null }
            .mapNotNull { msg ->
                AgentcoreMessageConverter.messageToPayload(msg, ignoreUnsupportedValues)
            }

        if (deltaPayloads.isEmpty()) return

        try {
            val request = CreateEventRequest {
                this.memoryId = this@AgentcoreChatHistoryProvider.memoryId
                this.actorId = actorId
                this.sessionId = sessionId
                payload = deltaPayloads
                eventTimestamp = Instant.now()
            }

            logger.debug("Creating an event with ${deltaPayloads.size} messages for actor $actorId and session $sessionId")
            val response = client.createEvent(request)
            logger.debug("Created an event with id ${response.event?.eventId}")
        } catch (e: SdkBaseException) {
            throw AgentcoreMemoryException.WriteException(
                "Failed to save messages for conversation: $conversationId",
                e
            )
        }
    }

    override suspend fun load(conversationId: String): List<Message> {
        val (actorId, sessionId) = conversationIdParser.parse(conversationId)

        logger.debug("Requesting events for actor $actorId and session $sessionId")
        val events: List<Event> = fetchEvents(actorId, sessionId, eventsLimit = totalEventsLimit)
        logger.debug("Received a response with ${events.size} events")

        return eventsToMessages(events)
    }

    private fun eventsToMessages(events: List<Event>): List<Message> {
        return events.flatMap { event ->
            val eventId = event.eventId
            val eventTimestamp = kotlin.time.Instant.fromEpochSeconds(
                event.eventTimestamp.epochSeconds,
                event.eventTimestamp.nanosecondsOfSecond.toLong()
            )
            event.payload.mapNotNull { payload ->
                when (payload) {
                    is PayloadType.Conversational -> {
                        AgentcoreMessageConverter.conversationalToMessage(
                            payload.value,
                            eventId = eventId,
                            eventTimestamp = eventTimestamp,
                            ignoreUnsupportedValues = ignoreUnsupportedValues
                        )
                    }

                    else -> {
                        if (ignoreUnsupportedValues) {
                            null
                        } else {
                            throw IllegalStateException(
                                "Unsupported payload type: ${payload::class.simpleName}"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Fetches all events for the given actor/session, paging through results
     * and reversing to chronological order (AgentCore returns newest-first).
     *
     * @param eventsLimit Optional cap on the total number of events to fetch.
     *   When `null`, all events are fetched.
     */
    private suspend fun fetchEvents(actorId: String, sessionId: String, eventsLimit: Int?): List<Event> {
        val allEvents = mutableListOf<Event>()
        var nextToken: String? = null
        val requestPageSize = if (eventsLimit != null) {
            minOf(pageSize, eventsLimit)
        } else {
            pageSize
        }

        try {
            do {
                val request = ListEventsRequest {
                    this.memoryId = this@AgentcoreChatHistoryProvider.memoryId
                    this.actorId = actorId
                    this.sessionId = sessionId
                    includePayloads = true
                    maxResults = requestPageSize
                    if (nextToken != null) {
                        this.nextToken = nextToken
                    }
                }

                val response = client.listEvents(request)
                allEvents.addAll(response.events)
                nextToken = response.nextToken

                if (eventsLimit != null && allEvents.size >= eventsLimit) {
                    val limited = allEvents.take(eventsLimit).toMutableList()
                    limited.reverse()
                    return limited
                }
            } while (nextToken != null)

            // AgentCore returns events in descending order (newest first),
            // reverse to chronological order for LLM context
            allEvents.reverse()
            return allEvents
        } catch (e: SdkBaseException) {
            throw AgentcoreMemoryException.ReadException(
                "Failed to fetch events for actor: $actorId, session: $sessionId",
                e
            )
        }
    }

    /**
     * Constants and utility functions for [AgentcoreChatHistoryProvider].
     */
    public companion object {
        /**
         * Default page size for listing events.
         */
        public const val DEFAULT_PAGE_SIZE: Int = 100
    }
}
