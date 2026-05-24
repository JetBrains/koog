package ai.koog.agents.longtermmemory.retrieval.augmentation

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for PromptAugmenter implementations
 */
class PromptAugmenterTest {

    private fun searchResults(vararg contents: String): List<SearchResult<TextDocument>> =
        contents.map { SearchResult(document = MemoryRecord(content = it), Score(1.0, ScoreMetric.COSINE_SIMILARITY)) }

    @Test
    fun testAugmentWithSystemMessageMode() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val relevantContext = searchResults(
            "Kotlin was developed by JetBrains",
            "Kotlin is 100% interoperable with Java"
        )

        val augmenter = SystemPromptAugmenter()
        val augmentedPrompt = augmenter.augment(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext
        )

        // The existing system message is augmented in place by appending extra MessagePart.Text
        // entries; no new system message is added.
        val systemMessages = augmentedPrompt.messages.filter { it is Message.System }
        assertEquals(1, systemMessages.size)
        val systemText = systemMessages[0].parts.filterIsInstance<MessagePart.Text>()
            .joinToString(separator = "\n") { it.text }
        // Original system content is preserved
        assertTrue(systemText.contains("You are a helpful assistant"))
        // And the appended retrieved-context section is present
        assertTrue(systemText.contains("Kotlin was developed by JetBrains"))
        assertTrue(systemText.contains("Kotlin is 100% interoperable with Java"))
        assertTrue(systemText.contains("Relevant information"))
    }

    @Test
    fun testAugmentWithUserMessageBeforeLastMode() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val relevantContext = searchResults("Kotlin was developed by JetBrains")

        val augmenter = UserPromptAugmenter()
        val augmentedPrompt = augmenter.augment(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext
        )

        // The existing user message is augmented in place by appending an extra MessagePart.Text;
        // no new user message is added.
        val userMessages = augmentedPrompt.messages.filter { it is Message.User }
        assertEquals(1, userMessages.size)
        val textParts = userMessages[0].parts.filterIsInstance<MessagePart.Text>()
        // Last part carries the retrieved context
        assertTrue(textParts.last().text.contains("Kotlin was developed by JetBrains"))
        // Original user content is preserved as a preceding part
        assertTrue(textParts.any { it.text == "What is Kotlin?" })
        // Original user content appears before the appended context
        assertTrue(textParts.indexOfFirst { it.text == "What is Kotlin?" } < textParts.lastIndex || textParts.size == 1)
    }

    @Test
    fun testUserPromptAugmenterReturnsOriginalWhenNoUserMessages() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
        }

        val relevantContext = searchResults("Kotlin was developed by JetBrains")

        val augmenter = UserPromptAugmenter()
        val augmentedPrompt = augmenter.augment(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext
        )

        // With no user messages, the prompt should remain unchanged
        assertEquals(originalPrompt.messages.size, augmentedPrompt.messages.size)
        assertEquals(originalPrompt.messages, augmentedPrompt.messages)
    }

    @Test
    fun testAugmentWithEmptyContext() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val augmenter = SystemPromptAugmenter()
        val augmentedPrompt = augmenter.augment(
            originalPrompt = originalPrompt,
            relevantContext = emptyList()
        )

        // With empty context, the prompt should remain unchanged
        assertEquals(originalPrompt.messages.size, augmentedPrompt.messages.size)
    }

    @Test
    fun testAugmentWithCustomTemplates() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("What is Kotlin?")
        }

        val relevantContext = searchResults("Kotlin was developed by JetBrains")
        val customSystemTemplate = "CUSTOM CONTEXT: {relevant_context}"

        val augmenter = SystemPromptAugmenter(template = customSystemTemplate)
        val augmentedPrompt = augmenter.augment(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext
        )

        // Verify the existing system message was augmented in place with the custom template
        val systemMessages = augmentedPrompt.messages.filter { it is Message.System }
        assertEquals(1, systemMessages.size)
        val systemText = systemMessages[0].parts.filterIsInstance<MessagePart.Text>()
            .joinToString(separator = "\n") { it.text }
        assertTrue(systemText.contains("You are a helpful assistant"))
        assertTrue(systemText.contains("CUSTOM CONTEXT:"))
        assertTrue(systemText.contains("Kotlin was developed by JetBrains"))
    }

    @Test
    fun testSystemPromptAugmenterReturnsOriginalWhenNoSystemMessages() {
        val originalPrompt = prompt("test") {
            user("What is Kotlin?")
        }

        val relevantContext = searchResults("Kotlin was developed by JetBrains")

        val augmenter = SystemPromptAugmenter()
        val augmentedPrompt = augmenter.augment(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext
        )

        // With no system messages, the prompt should remain unchanged
        assertEquals(originalPrompt.messages.size, augmentedPrompt.messages.size)
        assertEquals(originalPrompt.messages, augmentedPrompt.messages)
    }

    @Test
    fun testContextNumbering() {
        val originalPrompt = prompt("test") {
            system("You are a helpful assistant")
            user("Tell me about programming languages")
        }

        val relevantContext = searchResults(
            "First context item",
            "Second context item",
            "Third context item"
        )

        val augmenter = SystemPromptAugmenter()
        val augmentedPrompt = augmenter.augment(
            originalPrompt = originalPrompt,
            relevantContext = relevantContext
        )

        val systemMessages = augmentedPrompt.messages.filter { it is Message.System }
        assertEquals(1, systemMessages.size)
        val systemText = systemMessages[0].parts.filterIsInstance<MessagePart.Text>()
            .joinToString(separator = "\n") { it.text }
        assertTrue(systemText.contains("You are a helpful assistant"))
        assertTrue(systemText.contains("[1] First context item"))
        assertTrue(systemText.contains("[2] Second context item"))
        assertTrue(systemText.contains("[3] Third context item"))
    }

    @Test
    fun testFunInterfaceLambdaUsage() {
        val originalPrompt = prompt("test") {
            user("Hello")
        }

        val customAugmenter = PromptAugmenter { prompt, _ ->
            prompt // no-op augmenter
        }

        val result = customAugmenter.augment(originalPrompt, searchResults("some context"))
        assertEquals(originalPrompt.messages.size, result.messages.size)
    }
}
