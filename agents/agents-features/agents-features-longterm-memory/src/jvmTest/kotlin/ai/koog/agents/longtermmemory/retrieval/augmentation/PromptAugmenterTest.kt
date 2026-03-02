package ai.koog.agents.longtermmemory.retrieval.augmentation

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.agents.longtermmemory.retrieval.SearchResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for PromptAugmenter implementations
 */
class PromptAugmenterTest {

    private fun searchResults(vararg contents: String): List<SearchResult> =
        contents.map { SearchResult(record = MemoryRecord(content = it), similarity = 1.0) }

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

        // Verify a new system message with context was prepended, keeping the original intact
        val systemMessages = augmentedPrompt.messages.filter { it is Message.System }
        assertEquals(2, systemMessages.size)
        // First system message should contain the context
        assertTrue(systemMessages[0].content.contains("Kotlin was developed by JetBrains"))
        assertTrue(systemMessages[0].content.contains("Kotlin is 100% interoperable with Java"))
        assertTrue(systemMessages[0].content.contains("Relevant information"))
        // Second system message should be the original
        assertEquals("You are a helpful assistant", systemMessages[1].content)
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

        // Verify a new user message was inserted before the last user message
        val userMessages = augmentedPrompt.messages.filter { it is Message.User }
        assertEquals(2, userMessages.size)

        // First user message should contain the context
        assertTrue(userMessages[0].content.contains("Kotlin was developed by JetBrains"))
        // Second user message should be the original
        assertEquals("What is Kotlin?", userMessages[1].content)
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

        // Verify a new system message with the custom template was prepended
        val systemMessages = augmentedPrompt.messages.filter { it is Message.System }
        assertEquals(2, systemMessages.size)
        assertTrue(systemMessages[0].content.contains("CUSTOM CONTEXT:"))
        assertTrue(systemMessages[0].content.contains("Kotlin was developed by JetBrains"))
        // Original system message should remain unchanged
        assertEquals("You are a helpful assistant", systemMessages[1].content)
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
        assertEquals(2, systemMessages.size)
        assertTrue(systemMessages[0].content.contains("[1] First context item"))
        assertTrue(systemMessages[0].content.contains("[2] Second context item"))
        assertTrue(systemMessages[0].content.contains("[3] Third context item"))
        // Original system message should remain unchanged
        assertEquals("You are a helpful assistant", systemMessages[1].content)
    }

    @Test
    fun testFunInterfaceLambdaUsage() {
        val originalPrompt = prompt("test") {
            user("Hello")
        }

        val customAugmenter = PromptAugmenter { prompt, context ->
            prompt // no-op augmenter
        }

        val result = customAugmenter.augment(originalPrompt, searchResults("some context"))
        assertEquals(originalPrompt.messages.size, result.messages.size)
    }
}
