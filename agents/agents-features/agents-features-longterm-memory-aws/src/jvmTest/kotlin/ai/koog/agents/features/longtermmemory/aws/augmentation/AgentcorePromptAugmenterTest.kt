package ai.koog.agents.features.longtermmemory.aws.augmentation

import ai.koog.agents.features.longtermmemory.aws.AgentcoreMemoryRecord
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentcorePromptAugmenterTest {

    private fun result(content: String, strategy: AgentcoreMemoryStrategy): SearchResult<TextDocument> =
        SearchResult(
            document = AgentcoreMemoryRecord(content = content, strategy = strategy),
            score = Score(1.0, ScoreMetric.COSINE_SIMILARITY)
        )

    @Test
    fun testSummaryIsAppendedToLastUserMessage() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val augmented = AgentcorePromptAugmenter().augment(
            originalPrompt = originalPrompt,
            relevantContext = listOf(
                result(
                    "Previously discussed: Kotlin is a JVM language",
                    AgentcoreMemoryStrategy.SUMMARY
                )
            )
        )

        // The existing user message is augmented in place by appending an extra MessagePart.Text;
        // no new user message is added.
        val userMessages = augmented.messages.filter { it is Message.User }
        assertEquals(1, userMessages.size)
        val textParts = userMessages[0].parts.filterIsInstance<MessagePart.Text>()
        // The original user text is preserved as a preceding part
        assertTrue(textParts.any { it.text == "What is Kotlin?" })
        // The retrieved summary is in the last text part
        assertTrue(textParts.last().text.contains("Previously discussed: Kotlin is a JVM language"))
        // Original user content appears before the appended summary
        assertTrue(
            textParts.indexOfFirst { it.text == "What is Kotlin?" } < textParts.lastIndex ||
                textParts.size == 1
        )
    }

    @Test
    fun testSummaryNoOpWhenNoUserMessage() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
        }

        val augmented = AgentcorePromptAugmenter().augment(
            originalPrompt = originalPrompt,
            relevantContext = listOf(result("some summary", AgentcoreMemoryStrategy.SUMMARY))
        )

        // No user message exists; summaries are ignored on the user-side branch.
        assertEquals(originalPrompt.messages.size, augmented.messages.size)
        assertTrue(augmented.messages.none { it is Message.User })
    }

    @Test
    fun testSemanticIsAppendedToSystemMessage() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val augmented = AgentcorePromptAugmenter().augment(
            originalPrompt = originalPrompt,
            relevantContext = listOf(result("Kotlin was developed by JetBrains", AgentcoreMemoryStrategy.SEMANTIC))
        )

        val systemMessages = augmented.messages.filter { it is Message.System }
        assertEquals(1, systemMessages.size)
        val systemText = systemMessages[0].parts.filterIsInstance<MessagePart.Text>()
            .joinToString("\n") { it.text }
        assertTrue(systemText.contains("You are a helpful assistant"))
        assertTrue(systemText.contains("Kotlin was developed by JetBrains"))

        // User message is left untouched when only SEMANTIC context is provided.
        val userParts = augmented.messages.filterIsInstance<Message.User>()
            .single().parts.filterIsInstance<MessagePart.Text>()
        assertEquals(listOf("What is Kotlin?"), userParts.map { it.text })
    }

    @Test
    fun testEmptyContextReturnsOriginalPrompt() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val augmented = AgentcorePromptAugmenter().augment(
            originalPrompt = originalPrompt,
            relevantContext = emptyList()
        )

        assertEquals(originalPrompt.messages, augmented.messages)
    }
}
