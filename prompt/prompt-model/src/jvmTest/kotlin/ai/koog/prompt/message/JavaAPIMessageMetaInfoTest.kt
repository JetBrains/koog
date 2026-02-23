package ai.koog.prompt.message

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Tests for Java-friendly convenience methods in RequestMetaInfo and ResponseMetaInfo.
 * These tests verify that Java developers can create metadata without requiring Kotlin Clock.
 */
class JavaAPIMessageMetaInfoTest {

    @Test
    fun testRequestMetaInfoNowMethod() {
        // Test that RequestMetaInfo.now() creates an instance with current timestamp
        val metaInfo = RequestMetaInfo.now()
        
        assertNotNull(metaInfo)
        assertNotNull(metaInfo.timestamp)
        
        // Verify timestamp is recent (within last minute)
        val now = Clock.System.now()
        val diff = now - metaInfo.timestamp
        assertTrue(diff.inWholeSeconds < 60, "Timestamp should be recent")
    }

    @Test
    fun testResponseMetaInfoNowMethod() {
        // Test that ResponseMetaInfo.now() creates an instance with current timestamp
        val metaInfo = ResponseMetaInfo.now()
        
        assertNotNull(metaInfo)
        assertNotNull(metaInfo.timestamp)
        
        // Verify timestamp is recent (within last minute)
        val now = Clock.System.now()
        val diff = now - metaInfo.timestamp
        assertTrue(diff.inWholeSeconds < 60, "Timestamp should be recent")
    }

    @Test
    fun testResponseMetaInfoNowWithTokenCounts() {
        // Test that ResponseMetaInfo.now() works with token counts
        val metaInfo = ResponseMetaInfo.now(
            totalTokensCount = 100,
            inputTokensCount = 40,
            outputTokensCount = 60
        )
        
        assertNotNull(metaInfo)
        assertNotNull(metaInfo.timestamp)
        kotlin.test.assertEquals(100, metaInfo.totalTokensCount)
        kotlin.test.assertEquals(40, metaInfo.inputTokensCount)
        kotlin.test.assertEquals(60, metaInfo.outputTokensCount)
    }

    @Test
    fun testMessageCreationWithNowMethod() {
        // Test that messages can be created using the new convenience method
        val requestMeta = RequestMetaInfo.now()
        val responseMeta = ResponseMetaInfo.now()
        
        val systemMessage = Message.System("System prompt", requestMeta)
        val userMessage = Message.User("User input", requestMeta)
        val assistantMessage = Message.Assistant("Assistant response", responseMeta)
        
        assertNotNull(systemMessage)
        assertNotNull(userMessage)
        assertNotNull(assistantMessage)
        
        kotlin.test.assertEquals("System prompt", systemMessage.content)
        kotlin.test.assertEquals("User input", userMessage.content)
        kotlin.test.assertEquals("Assistant response", assistantMessage.content)
    }
}
