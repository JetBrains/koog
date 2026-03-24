package ai.koog.agents.features.chathistory.aws

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient
import aws.sdk.kotlin.services.bedrockagentcore.model.Content
import aws.sdk.kotlin.services.bedrockagentcore.model.Conversational
import aws.sdk.kotlin.services.bedrockagentcore.model.CreateEventRequest
import aws.sdk.kotlin.services.bedrockagentcore.model.CreateEventResponse
import aws.sdk.kotlin.services.bedrockagentcore.model.Event
import aws.sdk.kotlin.services.bedrockagentcore.model.ListEventsRequest
import aws.sdk.kotlin.services.bedrockagentcore.model.ListEventsResponse
import aws.sdk.kotlin.services.bedrockagentcore.model.PayloadType
import aws.sdk.kotlin.services.bedrockagentcore.model.Role
import aws.smithy.kotlin.runtime.time.Instant
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AgentcoreChatHistoryProviderTest {

    companion object {
        private val client = mockk<BedrockAgentCoreClient>(relaxed = true)
    }

    @BeforeTest
    fun setUp() {
        clearMocks(client)
    }

    // --- Config validation ---

    @Test
    fun testBlankMemoryIdThrows() {
        assertFailsWith<AgentcoreMemoryException.ConfigurationException> {
            AgentcoreChatHistoryProvider(client, memoryId = "")
        }
        assertFailsWith<AgentcoreMemoryException.ConfigurationException> {
            AgentcoreChatHistoryProvider(client, memoryId = "   ")
        }
    }

    // --- store: saves new messages (no eventId in metadata) ---

    @Test
    fun testStoreSavesAllNewMessages() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val requestSlot = mutableListOf<CreateEventRequest>()
        coEvery { client.createEvent(capture(requestSlot)) } returns CreateEventResponse {
            event = makeEvent("evt-1", emptyList())
        }

        provider.store(
            "actor:session",
            listOf(
                Message.User("Hello", RequestMetaInfo.Empty),
                Message.Assistant("Hi!", ResponseMetaInfo.Empty)
            )
        )

        assertEquals(1, requestSlot.size)
        val savedPayloads = requestSlot[0].payload!!
        assertEquals(2, savedPayloads.size)
        val first = savedPayloads[0] as PayloadType.Conversational
        assertEquals(Role.User, first.value.role)
        assertEquals("Hello", (first.value.content as Content.Text).value)
        val second = savedPayloads[1] as PayloadType.Conversational
        assertEquals(Role.Assistant, second.value.role)
        assertEquals("Hi!", (second.value.content as Content.Text).value)
    }

    @Test
    fun testStoreDoesNotCallListEvents() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        coEvery { client.createEvent(any<CreateEventRequest>()) } returns CreateEventResponse {
            event = makeEvent("evt-1", emptyList())
        }

        provider.store(
            "actor:session",
            listOf(
                Message.User("Hello", RequestMetaInfo.Empty),
                Message.Assistant("Hi!", ResponseMetaInfo.Empty)
            )
        )

        // store() should NOT fetch existing events — delta is based on eventId metadata
        coVerify(exactly = 0) { client.listEvents(any<ListEventsRequest>()) }
        coVerify(exactly = 1) { client.createEvent(any<CreateEventRequest>()) }
    }

    @Test
    fun testStoreEmptyMessagesDoesNothing() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        provider.store("actor:session", emptyList())

        coVerify(exactly = 0) { client.createEvent(any<CreateEventRequest>()) }
    }

    // --- store: ignores non-conversational messages ---

    @Test
    fun testStoreIgnoresNonConversationalMessages() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val requestSlot = mutableListOf<CreateEventRequest>()
        coEvery { client.createEvent(capture(requestSlot)) } returns CreateEventResponse {
            event = makeEvent("evt-1", emptyList())
        }

        val messages = listOf(
            Message.System("system prompt", RequestMetaInfo.Empty),
            Message.User("Hello", RequestMetaInfo.Empty),
            Message.Tool.Call(id = "1", tool = "t", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            Message.Assistant("Hi!", ResponseMetaInfo.Empty)
        )

        provider.store("actor:session", messages)

        coVerify(exactly = 1) { client.createEvent(any<CreateEventRequest>()) }
        val savedPayloads = requestSlot[0].payload!!
        assertEquals(2, savedPayloads.size)
    }

    @Test
    fun testStoreOnlyNonConversationalMessagesDoesNothing() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val messages = listOf(
            Message.System("system prompt", RequestMetaInfo.Empty),
            Message.Tool.Call(id = "1", tool = "t", content = "{}", metaInfo = ResponseMetaInfo.Empty)
        )

        provider.store("actor:session", messages)

        coVerify(exactly = 0) { client.createEvent(any<CreateEventRequest>()) }
    }

    @Test
    fun testStoreFailsOnUnsupportedValuesWhenConfigured() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1", ignoreUnsupportedValues = false)

        val messages = listOf(
            Message.System("system prompt", RequestMetaInfo.Empty)
        )

        assertFailsWith<IllegalStateException> {
            provider.store("actor:session", messages)
        }
    }

    // --- store: eventId-based delta detection ---

    @Test
    fun testStoreSkipsMessagesWithEventId() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val requestSlot = mutableListOf<CreateEventRequest>()
        coEvery { client.createEvent(capture(requestSlot)) } returns CreateEventResponse {
            event = makeEvent("evt-2", emptyList())
        }

        // Messages with eventId (loaded from AgentCore) + new message without eventId
        provider.store(
            "actor:session",
            listOf(
                userMsgWithEventId("Hello", "evt-1"),
                assistantMsgWithEventId("Hi!", "evt-1"),
                Message.User("Follow-up question", RequestMetaInfo.Empty)
            )
        )

        assertEquals(1, requestSlot.size)
        val savedPayloads = requestSlot[0].payload!!
        assertEquals(1, savedPayloads.size)
        val saved = savedPayloads[0] as PayloadType.Conversational
        assertEquals("Follow-up question", (saved.value.content as Content.Text).value)
    }

    @Test
    fun testStoreSkipsWhenAllMessagesHaveEventId() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        // All messages already persisted (have eventId)
        provider.store(
            "actor:session",
            listOf(
                userMsgWithEventId("Hello", "evt-1"),
                assistantMsgWithEventId("Hi!", "evt-1")
            )
        )

        coVerify(exactly = 0) { client.createEvent(any<CreateEventRequest>()) }
    }

    @Test
    fun testStoreDeltaWithMixedSystemAndConversationalMessages() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val requestSlot = mutableListOf<CreateEventRequest>()
        coEvery { client.createEvent(capture(requestSlot)) } returns CreateEventResponse {
            event = makeEvent("evt-2", emptyList())
        }

        // System message (filtered), persisted User (has eventId), new Assistant (no eventId)
        provider.store(
            "actor:session",
            listOf(
                Message.System("system prompt", RequestMetaInfo.Empty),
                userMsgWithEventId("Hello", "evt-1"),
                Message.Assistant("Hi!", ResponseMetaInfo.Empty)
            )
        )

        assertEquals(1, requestSlot.size)
        val savedPayloads = requestSlot[0].payload!!
        assertEquals(1, savedPayloads.size)
        val first = savedPayloads[0] as PayloadType.Conversational
        assertEquals(Role.Assistant, first.value.role)
        assertEquals("Hi!", (first.value.content as Content.Text).value)
    }

    @Test
    fun testStoreWindowedHistory_RemoteHas100_LocalHasLast20Plus1New() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val requestSlot = mutableListOf<CreateEventRequest>()
        coEvery { client.createEvent(capture(requestSlot)) } returns CreateEventResponse {
            event = makeEvent("evt-new", emptyList())
        }

        // Local window: last 20 messages with eventId (loaded from AgentCore) + 1 new
        val localWindow = (41..50).flatMap { i ->
            val eventIdx = ((i - 1) * 2) / 10 + 1
            listOf(
                userMsgWithEventId("user-$i", "evt-$eventIdx"),
                assistantMsgWithEventId("assistant-$i", "evt-$eventIdx")
            )
        } + listOf(Message.User("new-question", RequestMetaInfo.Empty))

        provider.store("actor:session", localWindow)

        assertEquals(1, requestSlot.size)
        val savedPayloads = requestSlot[0].payload!!
        assertEquals(1, savedPayloads.size)
        val saved = savedPayloads[0] as PayloadType.Conversational
        assertEquals("new-question", (saved.value.content as Content.Text).value)
    }

    @Test
    fun testStoreMultipleNewMessagesAfterPersisted() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val requestSlot = mutableListOf<CreateEventRequest>()
        coEvery { client.createEvent(capture(requestSlot)) } returns CreateEventResponse {
            event = makeEvent("evt-2", emptyList())
        }

        provider.store(
            "actor:session",
            listOf(
                userMsgWithEventId("Hello", "evt-1"),
                assistantMsgWithEventId("Hi!", "evt-1"),
                Message.User("Question 1", RequestMetaInfo.Empty),
                Message.Assistant("Answer 1", ResponseMetaInfo.Empty),
                Message.User("Question 2", RequestMetaInfo.Empty)
            )
        )

        assertEquals(1, requestSlot.size)
        val savedPayloads = requestSlot[0].payload!!
        assertEquals(3, savedPayloads.size)
        assertEquals(
            "Question 1",
            ((savedPayloads[0] as PayloadType.Conversational).value.content as Content.Text).value
        )
        assertEquals("Answer 1", ((savedPayloads[1] as PayloadType.Conversational).value.content as Content.Text).value)
        assertEquals(
            "Question 2",
            ((savedPayloads[2] as PayloadType.Conversational).value.content as Content.Text).value
        )
    }

    @Test
    fun testStoreEventIdMessagesInterleavedWithNew() = runTest {
        // eventId messages can appear in any order relative to new messages
        // (e.g., if preprocessors reorder). Only eventId presence matters.
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val requestSlot = mutableListOf<CreateEventRequest>()
        coEvery { client.createEvent(capture(requestSlot)) } returns CreateEventResponse {
            event = makeEvent("evt-2", emptyList())
        }

        provider.store(
            "actor:session",
            listOf(
                Message.User("new message", RequestMetaInfo.Empty),
                assistantMsgWithEventId("persisted message", "evt-1")
            )
        )

        // Only the new message (without eventId) should be saved
        assertEquals(1, requestSlot.size)
        val savedPayloads = requestSlot[0].payload!!
        assertEquals(1, savedPayloads.size)
        assertEquals(
            "new message",
            ((savedPayloads[0] as PayloadType.Conversational).value.content as Content.Text).value
        )
    }

    // --- load: loadAllEvents=true (default) fetches all events ---

    @Test
    fun testLoadReturnsAllMessages() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val event =
            makeEvent("evt-42", listOf(conversational(Role.User, "Hello"), conversational(Role.Assistant, "Hi!")))

        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = listOf(event)
            nextToken = null
        }

        val messages = provider.load("actor:session")

        assertEquals(2, messages.size)
        assertIs<Message.User>(messages[0])
        assertEquals("Hello", messages[0].content)
        assertIs<Message.Assistant>(messages[1])
        assertEquals("Hi!", messages[1].content)
    }

    @Test
    fun testLoadReturnsEmptyWhenNoEvents() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = emptyList()
            nextToken = null
        }

        val messages = provider.load("actor:session")
        assertEquals(0, messages.size)
    }

    @Test
    fun testLoadReturnsMessagesWithEventIdMetadata() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val pastTimestamp = Instant.fromEpochSeconds(1000000L, 0)
        val event = makeEvent(
            "evt-42",
            listOf(conversational(Role.User, "Hello"), conversational(Role.Assistant, "Hi!")),
            eventTimestamp = pastTimestamp
        )

        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = listOf(event)
            nextToken = null
        }

        val messages = provider.load("actor:session")

        assertEquals(2, messages.size)
        assertEquals("evt-42", AgentcoreMessageConverter.getEventId(messages[0]))
        assertEquals("evt-42", AgentcoreMessageConverter.getEventId(messages[1]))
        val expectedKotlinInstant = kotlin.time.Instant.fromEpochSeconds(
            pastTimestamp.epochSeconds,
            pastTimestamp.nanosecondsOfSecond.toLong()
        )
        assertEquals(expectedKotlinInstant, messages[0].metaInfo.timestamp)
    }

    // --- load: pagination and ordering ---

    @Test
    fun testLoadAllEventsReversesToChronological() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val newestEvent = makeEvent("evt-2", listOf(conversational(Role.Assistant, "response")))
        val oldestEvent = makeEvent("evt-1", listOf(conversational(Role.User, "question")))

        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = listOf(newestEvent, oldestEvent)
            nextToken = null
        }

        val messages = provider.load("actor:session")

        assertEquals(2, messages.size)
        assertIs<Message.User>(messages[0])
        assertEquals("question", messages[0].content)
        assertIs<Message.Assistant>(messages[1])
        assertEquals("response", messages[1].content)
    }

    @Test
    fun testLoadAllEventsPaginatesThroughAllPages() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1", pageSize = 1)

        val event2 = makeEvent("evt-2", listOf(conversational(Role.Assistant, "page1-msg")))
        val event1 = makeEvent("evt-1", listOf(conversational(Role.User, "page2-msg")))

        var callCount = 0
        coEvery { client.listEvents(any<ListEventsRequest>()) } answers {
            callCount++
            if (callCount == 1) {
                ListEventsResponse {
                    events = listOf(event2)
                    nextToken = "token-2"
                }
            } else {
                ListEventsResponse {
                    events = listOf(event1)
                    nextToken = null
                }
            }
        }

        val messages = provider.load("actor:session")

        assertEquals(2, messages.size)
        assertEquals("page2-msg", messages[0].content)
        assertEquals("page1-msg", messages[1].content)
        coVerify(exactly = 2) { client.listEvents(any<ListEventsRequest>()) }
    }

    @Test
    fun testLoadAllEventsRespectsEventsLimit() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1", totalEventsLimit = 1)

        val event1 = makeEvent("evt-1", listOf(conversational(Role.User, "msg1")))
        val event2 = makeEvent("evt-2", listOf(conversational(Role.Assistant, "msg2")))

        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = listOf(event1, event2)
            nextToken = "more"
        }

        val messages = provider.load("actor:session")

        assertEquals(1, messages.size)
    }

    // --- Unsupported value handling ---

    @Test
    fun testLoadIgnoresUnsupportedValuesByDefault() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        val event = makeEvent(
            "evt-1",
            listOf(
                conversational(Role.User, "hello"),
                conversational(Role.Tool, "tool output"),
                conversational(Role.Assistant, "hi")
            )
        )

        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = listOf(event)
            nextToken = null
        }

        val messages = provider.load("actor:session")

        assertEquals(2, messages.size)
        assertIs<Message.User>(messages[0])
        assertIs<Message.Assistant>(messages[1])
    }

    @Test
    fun testLoadFailsOnUnsupportedValuesWhenConfigured() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1", ignoreUnsupportedValues = false)

        val event = makeEvent("evt-1", listOf(conversational(Role.Tool, "tool output")))

        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = listOf(event)
            nextToken = null
        }

        assertFailsWith<IllegalStateException> {
            provider.load("actor:session")
        }
    }

    // --- Exception wrapping ---

    @Test
    fun testStoreWrapsAwsSdkExceptions() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        coEvery { client.createEvent(any<CreateEventRequest>()) } throws
            aws.smithy.kotlin.runtime.ServiceException("AWS error")

        assertFailsWith<AgentcoreMemoryException.WriteException> {
            provider.store("actor:session", listOf(Message.User("hi", RequestMetaInfo.Empty)))
        }
    }

    @Test
    fun testLoadWrapsAwsSdkExceptions() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        coEvery { client.listEvents(any<ListEventsRequest>()) } throws
            aws.smithy.kotlin.runtime.ServiceException("AWS error")

        assertFailsWith<AgentcoreMemoryException.ReadException> {
            provider.load("actor:session")
        }
    }

    // --- Default session ---

    @Test
    fun testCustomDefaultSession() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1", defaultSession = "my-session")

        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = emptyList()
            nextToken = null
        }

        provider.load("myActor")

        coVerify {
            client.listEvents(
                match<ListEventsRequest> {
                    it.sessionId == "my-session" && it.actorId == "myActor"
                }
            )
        }
    }

    // --- Round-trip: load → store only saves new messages ---

    @Test
    fun testRoundTripLoadThenStoreOnlySavesNew() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        // Simulate load: returns messages with eventId in metadata
        val event =
            makeEvent("evt-1", listOf(conversational(Role.User, "Hello"), conversational(Role.Assistant, "Hi!")))
        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = listOf(event)
            nextToken = null
        }

        val loaded = provider.load("actor:session")
        assertEquals(2, loaded.size)
        // Loaded messages should have eventId
        assertEquals("evt-1", AgentcoreMessageConverter.getEventId(loaded[0]))
        assertEquals("evt-1", AgentcoreMessageConverter.getEventId(loaded[1]))

        // Now store: loaded messages + one new message
        val storeRequestSlot = mutableListOf<CreateEventRequest>()
        coEvery { client.createEvent(capture(storeRequestSlot)) } returns CreateEventResponse {
            this.event = makeEvent("evt-2", emptyList())
        }

        val allMessages = loaded + Message.User("New question", RequestMetaInfo.Empty)
        provider.store("actor:session", allMessages)

        // Only the new message should be saved
        assertEquals(1, storeRequestSlot.size)
        val savedPayloads = storeRequestSlot[0].payload!!
        assertEquals(1, savedPayloads.size)
        assertEquals(
            "New question",
            ((savedPayloads[0] as PayloadType.Conversational).value.content as Content.Text).value
        )
    }

    @Test
    fun testRoundTripLoadThenStoreNoNewMessages() = runTest {
        val provider = AgentcoreChatHistoryProvider(client, memoryId = "mem-1")

        // Simulate load
        val event =
            makeEvent("evt-1", listOf(conversational(Role.User, "Hello"), conversational(Role.Assistant, "Hi!")))
        coEvery { client.listEvents(any<ListEventsRequest>()) } returns ListEventsResponse {
            events = listOf(event)
            nextToken = null
        }

        val loaded = provider.load("actor:session")

        // Store same messages back — nothing new to save
        provider.store("actor:session", loaded)

        coVerify(exactly = 0) { client.createEvent(any<CreateEventRequest>()) }
    }

    private fun makeEvent(
        eventId: String,
        payloads: List<PayloadType>,
        actorId: String = "actor",
        sessionId: String = "session",
        memoryId: String = "mem-1",
        eventTimestamp: Instant? = Instant.now()
    ): Event = Event {
        this.eventId = eventId
        this.actorId = actorId
        this.sessionId = sessionId
        this.memoryId = memoryId
        this.eventTimestamp = eventTimestamp
        this.payload = payloads
    }

    private fun conversational(role: Role, text: String): PayloadType.Conversational {
        return PayloadType.Conversational(
            Conversational {
                this.role = role
                this.content = Content.Text(text)
            }
        )
    }

    private fun userMsgWithEventId(text: String, eventId: String): Message.User {
        return Message.User(
            text,
            RequestMetaInfo(
                timestamp = kotlin.time.Clock.System.now(),
                metadata = JsonObject(mapOf(EVENT_ID_METADATA_KEY to JsonPrimitive(eventId)))
            )
        )
    }

    private fun assistantMsgWithEventId(text: String, eventId: String): Message.Assistant {
        return Message.Assistant(
            text,
            ResponseMetaInfo(
                timestamp = kotlin.time.Clock.System.now(),
                metadata = JsonObject(mapOf(EVENT_ID_METADATA_KEY to JsonPrimitive(eventId)))
            )
        )
    }
}
