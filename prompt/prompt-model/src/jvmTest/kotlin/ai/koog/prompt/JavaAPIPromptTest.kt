package ai.koog.prompt

import ai.koog.prompt.dsl.Prompt
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for @JavaAPI methods in Prompt class.
 * These tests verify that the Java-facing builder() method works correctly.
 */
class JavaAPIPromptTest {
    companion object {
        val ts: Instant = Instant.parse("2023-01-01T00:00:00Z")

        val testClock: Clock = object : Clock {
            override fun now(): Instant = ts
        }

        const val promptId = "test-prompt-id"
    }

    @Test
    fun testBuilderMethodWithId() {
        // Test that Prompt.builder(id) returns a proper PromptBuilder
        val builder = Prompt.builder(promptId)
        assertNotNull(builder)

        val prompt = builder
            .system("Test system message")
            .build()

        assertEquals(promptId, prompt.id)
        assertEquals(1, prompt.messages.size)
    }

    @Test
    fun testBuilderMethodWithIdAndClock() {
        // Test that Prompt.builder(id, clock) uses the provided clock
        val builder = Prompt.builder(promptId, testClock)
        assertNotNull(builder)

        val prompt = builder
            .system("Test system message")
            .user("Test user message")
            .build()

        assertEquals(promptId, prompt.id)
        assertEquals(2, prompt.messages.size)

        // Verify the timestamp uses testClock
        assertEquals(ts, prompt.messages[0].metaInfo.timestamp)
        assertEquals(ts, prompt.messages[1].metaInfo.timestamp)
    }

    @Test
    fun testBuilderMethodReturnsProperType() {
        // Verify that builder() returns the correct type and can be used fluently
        val prompt = Prompt.builder("fluent-test", testClock)
            .system("System")
            .user("User 1")
            .assistant("Assistant 1")
            .user("User 2")
            .build()

        assertEquals("fluent-test", prompt.id)
        assertEquals(4, prompt.messages.size)
    }

    @Test
    fun testBuilderWithDefaultClock() {
        // Test that when clock is not provided, it defaults correctly
        val builder = Prompt.builder("default-clock-test")
        val prompt = builder
            .system("Test")
            .build()

        assertEquals("default-clock-test", prompt.id)
        assertEquals(1, prompt.messages.size)
        // The timestamp should be set (not null or zero)
        assertTrue(prompt.messages[0].metaInfo.timestamp.toEpochMilliseconds() > 0)
    }

    @Test
    fun testBuilderMethodIsStaticAndAccessible() {
        // Verify that the builder method can be called as a static method
        // This is important for Java interop
        val builder1 = Prompt.builder("test-1")
        val builder2 = Prompt.builder("test-2", testClock)

        assertNotNull(builder1)
        assertNotNull(builder2)

        val prompt1 = builder1.system("Message 1").build()
        val prompt2 = builder2.system("Message 2").build()

        assertEquals("test-1", prompt1.id)
        assertEquals("test-2", prompt2.id)
    }

    @Test
    fun testBuilderCreatesIndependentInstances() {
        // Verify that each call to builder() creates a new independent instance
        val builder1 = Prompt.builder("instance-1", testClock)
        val builder2 = Prompt.builder("instance-2", testClock)

        builder1.system("System 1")
        builder2.system("System 2").user("User 2")

        val prompt1 = builder1.build()
        val prompt2 = builder2.build()

        assertEquals(1, prompt1.messages.size)
        assertEquals(2, prompt2.messages.size)
        assertEquals("System 1", prompt1.messages[0].content)
        assertEquals("System 2", prompt2.messages[0].content)
    }
}
