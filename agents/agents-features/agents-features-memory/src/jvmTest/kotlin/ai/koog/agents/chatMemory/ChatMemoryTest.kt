package ai.koog.agents.chatMemory

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.testing.tools.MockLLMBuilder
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatMemoryTest {

    private val preSeededHistory = listOf(
        Message.User("What is the capital of France?", RequestMetaInfo.Empty),
        Message.Assistant("The capital of France is Paris.", ResponseMetaInfo(Instant.DISTANT_PAST)),
        Message.User("And what about Germany?", RequestMetaInfo.Empty),
        Message.Assistant("The capital of Germany is Berlin.", ResponseMetaInfo(Instant.DISTANT_PAST)),
    )

    private class InMemoryChatHistoryProvider(
        val history: MutableMap<String, List<Message>> = mutableMapOf()
    ) : ChatHistoryProvider {
        val storeCalls = mutableListOf<Pair<String, List<Message>>>()
        val loadCalls = mutableListOf<String>()

        override suspend fun store(conversationId: String, messages: List<Message>) {
            storeCalls.add(conversationId to messages)
            history[conversationId] = messages
        }

        override suspend fun load(conversationId: String): List<Message> {
            loadCalls.add(conversationId)
            return history[conversationId] ?: emptyList()
        }
    }

    /**
     * A strategy that dumps the entire message history as the agent output.
     * This is useful for verifying that ChatMemory correctly loads
     * pre-seeded conversation history into the agent's prompt.
     */
    private val dumpHistoryStrategy = functionalStrategy<String, List<Message>> { input ->
        llm.writeSession {
            appendPrompt { user(input) }
        }

        getHistory()
    }

    private fun createGraphAgent(
        historyProvider: ChatHistoryProvider,
        windowSize: Int? = null,
        mockExecutorInit: MockLLMBuilder.() -> Unit
    ): AIAgent<String, String> {
        val mockExecutor = getMockExecutor(init = mockExecutorInit)
        return AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a helpful geography assistant.",
            maxIterations = 10,
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
                windowSize?.let { windowSize(it) }
            }
        }
    }

    private fun createFunctionalAgent(
        historyProvider: ChatHistoryProvider,
        windowSize: Int? = null,
        mockExecutorInit: MockLLMBuilder.() -> Unit
    ): AIAgent<String, List<Message>> {
        val mockExecutor = getMockExecutor(init = mockExecutorInit)
        return AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            strategy = dumpHistoryStrategy,
            systemPrompt = "You are a helpful geography assistant.",
            maxIterations = 10,
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
                windowSize(windowSize ?: Int.MAX_VALUE)
            }
        }
    }

    // ---- Basic Functional Tests ----

    @Test
    fun testChatMemoryFunctional() = runTest {
        val sessionId = "test-conversation"
        val historyProvider = InMemoryChatHistoryProvider(
            history = mutableMapOf()
        )

        var counter = 0
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("mock reply - $counter").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            strategy = dumpHistoryStrategy,
            systemPrompt = "You are a helpful geography assistant.",
            maxIterations = 10,
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
            }
        }

        val result = agent.run("What is the capital of Italy?", sessionId)

        counter++
        val secondRunResult = agent.run("What is the capital of Spain?", sessionId)

        assertTrue(result.isNotEmpty(), "History should not be empty")
        assertTrue(secondRunResult.isNotEmpty(), "History should not be empty")
    }

    // ---- Basic Graph Tests ----

    @Test
    fun testChatMemoryGraph() = runTest {
        val sessionId = "test-conversation"
        val historyProvider = InMemoryChatHistoryProvider(
            history = mutableMapOf()
        )

        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Paris is the capital of France.") onRequestContains "France"
            mockLLMAnswer("Berlin is the capital of Germany.") onRequestContains "Germany"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a helpful geography assistant.",
            maxIterations = 10,
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
            }
        }

        val firstRun = agent.run("What is the capital of France?", sessionId)
        val secondRun = agent.run("What is the capital of Germany?", sessionId)

        assertEquals("Berlin is the capital of Germany.", secondRun)
        assertTrue(historyProvider.storeCalls.isNotEmpty(), "ChatMemory should store history on strategy completion")
        assertEquals("Paris is the capital of France.", firstRun)

        val saved = historyProvider.load(sessionId)
        assertTrue(saved[0] is Message.User && saved[0].content.contains("France"), "First message should be the user asking about France")
        assertTrue(saved[1] is Message.Assistant && saved[1].content.contains("Paris"), "Second message should be the assistant replying about France")
        assertTrue(saved[2] is Message.User && saved[2].content.contains("Germany"), "Third message should be the user asking about Germany")
        assertTrue(saved[3] is Message.Assistant && saved[3].content.contains("Berlin"), "Fourth message should be the assistant replying about Germany")
    }

    // ---- Load/Store Call Tracking ----

    @Test
    fun testLoadIsCalledBeforeEachRun() = runTest {
        val sessionId = "session-1"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("First question", sessionId)
        agent.run("Second question", sessionId)
        agent.run("Third question", sessionId)

        assertEquals(3, historyProvider.loadCalls.size, "Load should be called once per run")
        assertTrue(historyProvider.loadCalls.all { it == sessionId }, "All load calls should use the same session ID")
    }

    @Test
    fun testStoreIsCalledAfterEachRun() = runTest {
        val sessionId = "session-1"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("First question", sessionId)
        assertEquals(1, historyProvider.storeCalls.size, "Store should be called after first run")

        agent.run("Second question", sessionId)
        assertEquals(2, historyProvider.storeCalls.size, "Store should be called after second run")

        assertTrue(historyProvider.storeCalls.all { it.first == sessionId }, "All store calls should use the same session ID")
    }

    @Test
    fun testStoreIsCalledEvenOnFirstRunWithEmptyHistory() = runTest {
        val sessionId = "new-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Hello", sessionId)

        assertEquals(1, historyProvider.loadCalls.size, "Load should be called on first run")
        assertEquals(1, historyProvider.storeCalls.size, "Store should be called even on first run")
        assertEquals(sessionId, historyProvider.storeCalls[0].first)
    }

    // ---- Pre-seeded History ----

    @Test
    fun testPreSeededHistoryIsLoadedInFunctionalStrategy() = runTest {
        val sessionId = "pre-seeded-session"
        val historyProvider = InMemoryChatHistoryProvider(
            history = mutableMapOf(sessionId to preSeededHistory)
        )

        val agent = createFunctionalAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val history = agent.run("What about Italy?", sessionId)

        val userMessages = history.filterIsInstance<Message.User>()
        assertTrue(
            userMessages.any { it.content.contains("France") },
            "Pre-seeded history about France should be present"
        )
        assertTrue(
            userMessages.any { it.content.contains("Germany") },
            "Pre-seeded history about Germany should be present"
        )
        assertTrue(
            userMessages.any { it.content.contains("Italy") },
            "New question about Italy should be present"
        )
    }

    @Test
    fun testPreSeededHistoryIsLoadedInGraphStrategy() = runTest {
        val sessionId = "pre-seeded-graph-session"
        val historyProvider = InMemoryChatHistoryProvider(
            history = mutableMapOf(sessionId to preSeededHistory)
        )

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("Rome is the capital of Italy.") onRequestContains "Italy"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val result = agent.run("What about Italy?", sessionId)
        assertEquals("Rome is the capital of Italy.", result)

        val saved = historyProvider.load(sessionId)
        assertTrue(saved.size > preSeededHistory.size, "Saved history should contain pre-seeded + new messages")

        val userContents = saved.filterIsInstance<Message.User>().map { it.content }
        assertTrue(userContents.any { it.contains("France") }, "Pre-seeded France question should be preserved")
        assertTrue(userContents.any { it.contains("Germany") }, "Pre-seeded Germany question should be preserved")
        assertTrue(userContents.any { it.contains("Italy") }, "New Italy question should be appended")
    }

    // ---- Session Isolation ----

    @Test
    fun testDifferentSessionIdsAreIsolated() = runTest {
        val sessionA = "session-A"
        val sessionB = "session-B"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("Paris is the capital of France.") onRequestContains "France"
            mockLLMAnswer("Tokyo is the capital of Japan.") onRequestContains "Japan"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("What is the capital of France?", sessionA)
        agent.run("What is the capital of Japan?", sessionB)

        val savedA = historyProvider.history[sessionA] ?: error("Session A should have saved history")
        val savedB = historyProvider.history[sessionB] ?: error("Session B should have saved history")

        assertTrue(
            savedA.any { it.content.contains("France") },
            "Session A should contain France question"
        )
        assertTrue(
            savedA.none { it.content.contains("Japan") },
            "Session A should NOT contain Japan question"
        )
        assertTrue(
            savedB.any { it.content.contains("Japan") },
            "Session B should contain Japan question"
        )
        assertTrue(
            savedB.none { it.content.contains("France") },
            "Session B should NOT contain France question"
        )
    }

    @Test
    fun testInterleavedSessionsDoNotCrossContaminate() = runTest {
        val sessionA = "interleaved-A"
        val sessionB = "interleaved-B"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("Answer A1") onRequestContains "A1"
            mockLLMAnswer("Answer B1") onRequestContains "B1"
            mockLLMAnswer("Answer A2") onRequestContains "A2"
            mockLLMAnswer("Answer B2") onRequestContains "B2"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Question A1", sessionA)
        agent.run("Question B1", sessionB)
        agent.run("Question A2", sessionA)
        agent.run("Question B2", sessionB)

        val savedA = historyProvider.history[sessionA]!!
        val savedB = historyProvider.history[sessionB]!!

        val aContents = savedA.map { it.content }
        val bContents = savedB.map { it.content }

        assertTrue(aContents.any { it.contains("A1") }, "Session A should contain A1")
        assertTrue(aContents.any { it.contains("A2") }, "Session A should contain A2")
        assertTrue(aContents.none { it.contains("B1") }, "Session A should NOT contain B1")
        assertTrue(aContents.none { it.contains("B2") }, "Session A should NOT contain B2")

        assertTrue(bContents.any { it.contains("B1") }, "Session B should contain B1")
        assertTrue(bContents.any { it.contains("B2") }, "Session B should contain B2")
        assertTrue(bContents.none { it.contains("A1") }, "Session B should NOT contain A1")
        assertTrue(bContents.none { it.contains("A2") }, "Session B should NOT contain A2")
    }

    // ---- History Accumulation ----

    @Test
    fun testHistoryAccumulatesAcrossMultipleRuns() = runTest {
        val sessionId = "accumulation-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("Reply 1") onRequestContains "Question 1"
            mockLLMAnswer("Reply 2") onRequestContains "Question 2"
            mockLLMAnswer("Reply 3") onRequestContains "Question 3"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Question 1", sessionId)
        val afterFirst = historyProvider.history[sessionId]!!.toList()

        agent.run("Question 2", sessionId)
        val afterSecond = historyProvider.history[sessionId]!!.toList()

        agent.run("Question 3", sessionId)
        val afterThird = historyProvider.history[sessionId]!!.toList()

        assertTrue(afterSecond.size > afterFirst.size, "History should grow after second run")
        assertTrue(afterThird.size > afterSecond.size, "History should grow after third run")

        val allContents = afterThird.map { it.content }
        assertTrue(allContents.any { it.contains("Question 1") })
        assertTrue(allContents.any { it.contains("Reply 1") })
        assertTrue(allContents.any { it.contains("Question 2") })
        assertTrue(allContents.any { it.contains("Reply 2") })
        assertTrue(allContents.any { it.contains("Question 3") })
        assertTrue(allContents.any { it.contains("Reply 3") })
    }

    @Test
    fun testHistoryMaintainsChronologicalOrder() = runTest {
        val sessionId = "order-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("Reply A") onRequestContains "First"
            mockLLMAnswer("Reply B") onRequestContains "Second"
            mockLLMAnswer("Reply C") onRequestContains "Third"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("First message", sessionId)
        agent.run("Second message", sessionId)
        agent.run("Third message", sessionId)

        val saved = historyProvider.history[sessionId]!!
        val userMessages = saved.filterIsInstance<Message.User>().map { it.content }

        val firstIdx = userMessages.indexOfFirst { it.contains("First") }
        val secondIdx = userMessages.indexOfFirst { it.contains("Second") }
        val thirdIdx = userMessages.indexOfFirst { it.contains("Third") }

        assertTrue(firstIdx < secondIdx, "First message should come before Second")
        assertTrue(secondIdx < thirdIdx, "Second message should come before Third")
    }

    // ---- Message Type Verification ----

    @Test
    fun testStoredHistoryAlternatesUserAndAssistantMessages() = runTest {
        val sessionId = "alternating-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Hello", sessionId)

        val saved = historyProvider.history[sessionId]!!
        val nonSystemMessages = saved.filter { it !is Message.System }

        for (i in nonSystemMessages.indices) {
            if (i % 2 == 0) {
                assertIs<Message.User>(nonSystemMessages[i], "Even-indexed non-system message at $i should be User")
            } else {
                assertIs<Message.Assistant>(nonSystemMessages[i], "Odd-indexed non-system message at $i should be Assistant")
            }
        }
    }

    @Test
    fun testStoredHistoryContainsUserInputContent() = runTest {
        val sessionId = "content-check-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("42 is the answer").asDefaultResponse
        }

        agent.run("What is the meaning of life?", sessionId)

        val saved = historyProvider.history[sessionId]!!
        val userMessages = saved.filterIsInstance<Message.User>()
        assertTrue(
            userMessages.any { it.content.contains("meaning of life") },
            "User input should be stored in history"
        )

        val assistantMessages = saved.filterIsInstance<Message.Assistant>()
        assertTrue(
            assistantMessages.any { it.content.contains("42") },
            "Assistant reply should be stored in history"
        )
    }

    // ---- InMemoryChatHistoryProvider ----

    @Test
    fun testInMemoryChatHistoryProviderReturnsEmptyListForUnknownSession() = runTest {
        val provider = InMemoryChatHistoryProvider()
        val result = provider.load("unknown-session")
        assertTrue(result.isEmpty(), "InMemoryChatHistoryProvider should return empty list for unknown session")
    }

    @Test
    fun testInMemoryChatHistoryProviderStoresAndLoads() = runTest {
        val provider = InMemoryChatHistoryProvider()
        provider.store("session-1", preSeededHistory)
        val result = provider.load("session-1")
        assertEquals(preSeededHistory, result, "Should return exactly what was stored")
    }

    @Test
    fun testInMemoryChatHistoryProviderOverwritesOnStore() = runTest {
        val provider = InMemoryChatHistoryProvider()
        provider.store("session-1", preSeededHistory)
        val newHistory = listOf(Message.User("New message", RequestMetaInfo.Empty))
        provider.store("session-1", newHistory)
        val result = provider.load("session-1")
        assertEquals(newHistory, result, "Store should overwrite previous history")
    }

    @Test
    fun testDefaultProviderRunsWithoutExplicitConfiguration() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a helpful assistant.",
            maxIterations = 10,
        ) {
            install(ChatMemory)
        }

        val result = agent.run("Hello", "session-1")
        assertEquals("mock reply", result)
    }

    // ---- Edge Cases ----

    @Test
    fun testEmptyUserInput() = runTest {
        val sessionId = "empty-input-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val result = agent.run("", sessionId)
        assertEquals("mock reply", result)
        assertTrue(historyProvider.storeCalls.isNotEmpty(), "Store should still be called for empty input")
    }

    @Test
    fun testVeryLongUserInput() = runTest {
        val sessionId = "long-input-session"
        val historyProvider = InMemoryChatHistoryProvider()
        val longInput = "x".repeat(10_000)

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val result = agent.run(longInput, sessionId)
        assertEquals("mock reply", result)

        val saved = historyProvider.history[sessionId]!!
        assertTrue(
            saved.any { it.content.contains(longInput) },
            "Long input should be preserved in history"
        )
    }

    @Test
    fun testSessionIdWithSpecialCharacters() = runTest {
        val sessionId = "session/with:special@chars#123!&spaces too"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Hello", sessionId)

        assertTrue(historyProvider.history.containsKey(sessionId), "Special character session ID should work")
        assertEquals(sessionId, historyProvider.loadCalls[0], "Load should be called with exact session ID")
        assertEquals(sessionId, historyProvider.storeCalls[0].first, "Store should be called with exact session ID")
    }

    @Test
    fun testMultipleRunsWithSameInputProduceGrowingHistory() = runTest {
        val sessionId = "same-input-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Hello", sessionId)
        val sizeAfterFirst = historyProvider.history[sessionId]!!.size

        agent.run("Hello", sessionId)
        val sizeAfterSecond = historyProvider.history[sessionId]!!.size

        assertTrue(
            sizeAfterSecond > sizeAfterFirst,
            "History should grow even when input is repeated"
        )
    }

    @Test
    fun testFunctionalStrategySeesPreSeededHistoryBeforeNewMessage() = runTest {
        val sessionId = "pre-seeded-order"
        val historyProvider = InMemoryChatHistoryProvider(
            history = mutableMapOf(sessionId to preSeededHistory)
        )

        val agent = createFunctionalAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val history = agent.run("New question", sessionId)

        val nonSystemMessages = history.filter { it !is Message.System }
        assertTrue(nonSystemMessages.size >= preSeededHistory.size + 1, "Should have pre-seeded + new messages")

        val lastUserMessage = nonSystemMessages.filterIsInstance<Message.User>().last()
        assertTrue(
            lastUserMessage.content.contains("New question"),
            "Last user message should be the new question"
        )
    }

    @Test
    fun testStoreOverwritesPreviousHistoryForSameSession() = runTest {
        val sessionId = "overwrite-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("Reply 1") onRequestContains "First"
            mockLLMAnswer("Reply 2") onRequestContains "Second"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("First", sessionId)
        val firstStoredHistory = historyProvider.history[sessionId]!!.toList()

        agent.run("Second", sessionId)
        val secondStoredHistory = historyProvider.history[sessionId]!!.toList()

        assertTrue(
            secondStoredHistory.size > firstStoredHistory.size,
            "Second stored history should be larger (includes first run messages)"
        )
        assertTrue(
            secondStoredHistory != firstStoredHistory,
            "Store should overwrite with the full accumulated history"
        )
    }

    @Test
    fun testProviderLoadCalledWithCorrectSessionId() = runTest {
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Hello", "session-alpha")
        agent.run("Hello", "session-beta")
        agent.run("Hello", "session-gamma")

        assertEquals(
            listOf("session-alpha", "session-beta", "session-gamma"),
            historyProvider.loadCalls,
            "Load should be called with correct session IDs in order"
        )
    }

    @Test
    fun testProviderStoreCalledWithCorrectSessionId() = runTest {
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Hello", "session-alpha")
        agent.run("Hello", "session-beta")

        assertEquals("session-alpha", historyProvider.storeCalls[0].first)
        assertEquals("session-beta", historyProvider.storeCalls[1].first)
    }

    // ---- Window Size ----

    @Test
    fun testWindowSizeNullKeepsAllMessages() = runTest {
        val sessionId = "no-window-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider, windowSize = null) {
            mockLLMAnswer("Reply 1") onRequestContains "Q1"
            mockLLMAnswer("Reply 2") onRequestContains "Q2"
            mockLLMAnswer("Reply 3") onRequestContains "Q3"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Q1", sessionId)
        agent.run("Q2", sessionId)
        agent.run("Q3", sessionId)

        val saved = historyProvider.history[sessionId]!!
        val userMessages = saved.filterIsInstance<Message.User>()
        assertEquals(3, userMessages.size, "All 3 user messages should be kept without window limit")
    }

    @Test
    fun testWindowSizeTruncatesStoredHistory() = runTest {
        val sessionId = "window-store-session"
        val historyProvider = InMemoryChatHistoryProvider()

        // Window of 2 messages: only the last 2 messages should be stored
        val agent = createGraphAgent(historyProvider, windowSize = 2) {
            mockLLMAnswer("Reply 1") onRequestContains "Q1"
            mockLLMAnswer("Reply 2") onRequestContains "Q2"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Q1", sessionId)
        agent.run("Q2", sessionId)

        val saved = historyProvider.history[sessionId]!!
        assertEquals(2, saved.size, "Only the last 2 messages should be stored")
    }

    @Test
    fun testWindowSizeTruncatesLoadedHistory() = runTest {
        val sessionId = "window-load-session"
        // Pre-seed with 4 messages
        val historyProvider = InMemoryChatHistoryProvider(
            history = mutableMapOf(sessionId to preSeededHistory)
        )

        // Window of 2: only 2 of the 4 pre-seeded messages should be loaded
        val agent = createFunctionalAgent(historyProvider, windowSize = 2) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val history = agent.run("New question", sessionId)
        val nonSystemMessages = history.filter { it !is Message.System }

        // Should see 2 windowed messages + the new user message = 3
        assertTrue(
            nonSystemMessages.size <= 2 + 1,
            "Loaded history should be windowed to 2 messages plus the new question, but was ${nonSystemMessages.size}"
        )
        // The pre-seeded France messages (earliest) should be dropped
        assertTrue(
            nonSystemMessages.none { it.content.contains("France") },
            "Oldest messages about France should be outside the window"
        )
    }

    @Test
    fun testWindowSizeKeepsNewestMessages() = runTest {
        val sessionId = "window-newest-session"
        val historyProvider = InMemoryChatHistoryProvider()

        // Window of 4: each run produces a user+assistant pair (2 messages)
        // After 3 runs we have 6 messages, but only the last 4 should be kept
        val agent = createGraphAgent(historyProvider, windowSize = 4) {
            mockLLMAnswer("Reply 1") onRequestContains "Q1"
            mockLLMAnswer("Reply 2") onRequestContains "Q2"
            mockLLMAnswer("Reply 3") onRequestContains "Q3"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Q1", sessionId)
        agent.run("Q2", sessionId)
        agent.run("Q3", sessionId)

        val saved = historyProvider.history[sessionId]!!
        assertEquals(4, saved.size, "Window should keep exactly 4 messages")

        val contents = saved.map { it.content }
        assertTrue(contents.none { it.contains("Q1") }, "Q1 should have been truncated")
        assertTrue(contents.none { it.contains("Reply 1") }, "Reply 1 should have been truncated")
        assertTrue(contents.any { it.contains("Q2") }, "Q2 should be within window")
        assertTrue(contents.any { it.contains("Q3") }, "Q3 should be within window")
    }

    @Test
    fun testWindowSizeLargerThanHistoryKeepsAll() = runTest {
        val sessionId = "large-window-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider, windowSize = 100) {
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Hello", sessionId)

        val saved = historyProvider.history[sessionId]!!
        assertTrue(saved.isNotEmpty(), "All messages should be kept when window > history size")
        assertTrue(saved.size <= 100, "Should not exceed window size")
    }

    @Test
    fun testWindowSizeOfOneKeepsOnlyLastMessage() = runTest {
        val sessionId = "window-one-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider, windowSize = 1) {
            mockLLMAnswer("Reply 1") onRequestContains "Q1"
            mockLLMAnswer("Reply 2") onRequestContains "Q2"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Q1", sessionId)
        agent.run("Q2", sessionId)

        val saved = historyProvider.history[sessionId]!!
        assertEquals(1, saved.size, "Window of 1 should keep only the last message")
    }

    @Test
    fun testWindowSizeAppliedConsistentlyAcrossRuns() = runTest {
        val sessionId = "window-consistent-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider, windowSize = 4) {
            mockLLMAnswer("Reply 1") onRequestContains "Q1"
            mockLLMAnswer("Reply 2") onRequestContains "Q2"
            mockLLMAnswer("Reply 3") onRequestContains "Q3"
            mockLLMAnswer("Reply 4") onRequestContains "Q4"
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        agent.run("Q1", sessionId)
        assertEquals(2, historyProvider.history[sessionId]!!.size, "After 1 run: 2 messages")

        agent.run("Q2", sessionId)
        assertEquals(4, historyProvider.history[sessionId]!!.size, "After 2 runs: 4 messages (at window limit)")

        agent.run("Q3", sessionId)
        assertEquals(4, historyProvider.history[sessionId]!!.size, "After 3 runs: still 4 messages (window applied)")

        agent.run("Q4", sessionId)
        assertEquals(4, historyProvider.history[sessionId]!!.size, "After 4 runs: still 4 messages (window applied)")

        val saved = historyProvider.history[sessionId]!!
        val contents = saved.map { it.content }
        assertTrue(contents.none { it.contains("Q1") }, "Q1 should be outside window")
        assertTrue(contents.none { it.contains("Q2") }, "Q2 should be outside window")
        assertTrue(contents.any { it.contains("Q3") }, "Q3 should be in window")
        assertTrue(contents.any { it.contains("Q4") }, "Q4 should be in window")
    }

    // ---- filterMessages ----

    @Test
    fun testFilterMessagesKeepsMatchingMessages() = runTest {
        val sessionId = "filter-session"
        val historyProvider = InMemoryChatHistoryProvider()

        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Short").onRequestContains("short")
            mockLLMAnswer("This is a very long reply that should be filtered out").onRequestContains("long")
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a helpful assistant.",
            maxIterations = 10,
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
                filterMessages { it.content.length <= 20 }
            }
        }

        agent.run("short", sessionId)
        agent.run("long", sessionId)

        val saved = historyProvider.history[sessionId]!!
        assertTrue(
            saved.none { it.content.contains("very long reply") },
            "Long reply should have been filtered out"
        )
        assertTrue(
            saved.any { it.content == "Short" },
            "Short reply should be kept"
        )
    }

    @Test
    fun testFilterMessagesOnlyUserMessages() = runTest {
        val sessionId = "filter-user-only"
        val historyProvider = InMemoryChatHistoryProvider()

        val agent = createGraphAgent(historyProvider) {
            mockLLMAnswer("Reply 1").onRequestContains("Q1")
            mockLLMAnswer("Reply 2").onRequestContains("Q2")
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        // This agent does NOT use filterMessages, just to get baseline data.
        // We need a separate agent that filters.
        val filterAgent = run {
            val exec = getMockExecutor {
                mockLLMAnswer("Reply 1").onRequestContains("Q1")
                mockLLMAnswer("Reply 2").onRequestContains("Q2")
                mockLLMAnswer("mock reply").asDefaultResponse
            }
            AIAgent(
                promptExecutor = exec,
                llmModel = OpenAIModels.Chat.GPT4oMini,
                systemPrompt = "You are a helpful assistant.",
                maxIterations = 10,
            ) {
                install(ChatMemory) {
                    chatHistoryProvider = historyProvider
                    filterMessages { it is Message.User }
                }
            }
        }

        filterAgent.run("Q1", sessionId)
        filterAgent.run("Q2", sessionId)

        val saved = historyProvider.history[sessionId]!!
        assertTrue(
            saved.all { it is Message.User },
            "Only user messages should remain after filtering"
        )
    }

    @Test
    fun testWindowSizeThenFilterMessages() = runTest {
        // windowSize(4) first, then filterMessages(User)
        // Since preprocessors run at both load AND store:
        //   - Store after each run: window(4) keeps up to 4 raw messages, then filter drops Assistant
        //   - The filter at store-time means the stored list only contains User messages,
        //     so on subsequent loads the list is already short and the window rarely trims.
        // Result after 3 runs: all 3 user messages [Q1, Q2, Q3] are preserved.
        val sessionId = "window-then-filter"
        val historyProvider = InMemoryChatHistoryProvider()

        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Reply 1").onRequestContains("Q1")
            mockLLMAnswer("Reply 2").onRequestContains("Q2")
            mockLLMAnswer("Reply 3").onRequestContains("Q3")
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a helpful assistant.",
            maxIterations = 10,
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
                windowSize(4)
                filterMessages { it is Message.User }
            }
        }

        agent.run("Q1", sessionId)
        agent.run("Q2", sessionId)
        agent.run("Q3", sessionId)

        val saved = historyProvider.history[sessionId]!!
        assertTrue(saved.all { it is Message.User }, "All saved messages should be User")
        // All 3 user messages fit within the window because the filter keeps the list short
        assertEquals(3, saved.size, "All 3 user messages should be kept")
        assertTrue(saved.any { it.content.contains("Q1") }, "Q1 should be present")
        assertTrue(saved.any { it.content.contains("Q2") }, "Q2 should be present")
        assertTrue(saved.any { it.content.contains("Q3") }, "Q3 should be present")
    }

    @Test
    fun testFilterMessagesThenWindowSize() = runTest {
        // filterMessages(User) first, then windowSize(2)
        // Since preprocessors run at both load AND store:
        //   - Store after each run: filter drops Assistant messages, then window(2) keeps last 2
        // Result after 3 runs: exactly [Q2, Q3] — the last 2 user messages.
        val sessionId = "filter-then-window"
        val historyProvider = InMemoryChatHistoryProvider()

        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Reply 1").onRequestContains("Q1")
            mockLLMAnswer("Reply 2").onRequestContains("Q2")
            mockLLMAnswer("Reply 3").onRequestContains("Q3")
            mockLLMAnswer("mock reply").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a helpful assistant.",
            maxIterations = 10,
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
                filterMessages { it is Message.User }
                windowSize(2)
            }
        }

        agent.run("Q1", sessionId)
        agent.run("Q2", sessionId)
        agent.run("Q3", sessionId)

        val saved = historyProvider.history[sessionId]!!
        // Filter keeps only User messages: Q1, Q2, Q3
        // Window of 2 keeps: Q2, Q3
        assertEquals(2, saved.size, "Exactly 2 messages after filter-then-window")
        assertTrue(saved.all { it is Message.User }, "All should be User messages")
        assertTrue(saved.none { it.content.contains("Q1") }, "Q1 should be outside the window")
        assertTrue(saved.any { it.content.contains("Q2") }, "Q2 should be in the window")
        assertTrue(saved.any { it.content.contains("Q3") }, "Q3 should be in the window")
    }
}
