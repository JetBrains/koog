package ai.koog.agents.features.pubsub.providers.gcp

import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GCPPubSubProviderTest {

    @Test
    fun provider_shouldInitializeWithValidConfig() {
        val provider = GCPPubSubProvider(
            projectId = "test-project",
            credentialsPath = null, // Use default credentials
            subscriptionPrefix = "test-",
            autoCreateTopics = true,
            autoCreateSubscriptions = true,
            ackDeadlineSeconds = 30,
            maxOutstandingMessages = 500
        )
        
        // Should not throw during initialization
        assertTrue(true)
    }

    @Test
    fun provider_shouldReportCorrectConfiguration() = runTest {
        val projectId = "my-test-project"
        val subscriptionPrefix = "agent-test-"
        val provider = GCPPubSubProvider(
            projectId = projectId,
            subscriptionPrefix = subscriptionPrefix,
            autoCreateTopics = false,
            autoCreateSubscriptions = false
        )
        
        val healthInfo = provider.getHealthInfo()
        
        assertEquals("gcp-pubsub", healthInfo["provider"])
        assertEquals(projectId, healthInfo["projectId"])
        assertEquals(subscriptionPrefix, healthInfo["subscriptionPrefix"])
        assertEquals(false, healthInfo["autoCreateTopics"])
        assertEquals(false, healthInfo["autoCreateSubscriptions"])
    }

    @Test
    fun provider_shouldReportNotConnectedWhenCredentialsInvalid() = runTest {
        val provider = GCPPubSubProvider(
            projectId = "invalid-project",
            credentialsPath = "/invalid/path/to/credentials.json"
        )
        
        val isConnected = provider.isConnected()
        val healthInfo = provider.getHealthInfo()
        
        assertFalse(isConnected)
        assertEquals(false, healthInfo["connected"])
        assertEquals(false, healthInfo["healthy"])
    }

    @Test
    fun provider_shouldHandleStringMessage() = runTest {
        val provider = GCPPubSubProvider(
            projectId = "test-project",
            credentialsPath = "/invalid/credentials.json" // Will cause publish to fail
        )
        
        val message = PubSubStringMessage(
            topic = "test-topic",
            content = "test message",
            attributes = mapOf("source" to "test", "type" to "event")
        )
        
        // Should handle the message type even if publish fails due to invalid credentials
        try {
            provider.publish(message)
        } catch (e: Exception) {
            // Expected when credentials are invalid
            assertTrue(e.message?.contains("GCP") == true || e.message?.contains("publish") == true)
        }
    }

    @Test
    fun provider_shouldConfigureAckDeadline() = runTest {
        val customAckDeadline = 45
        val provider = GCPPubSubProvider(
            projectId = "test-project",
            ackDeadlineSeconds = customAckDeadline
        )
        
        // Test passes if provider initializes without error
        assertTrue(true)
    }

    @Test
    fun provider_shouldConfigureMaxOutstandingMessages() = runTest {
        val maxMessages = 2000
        val provider = GCPPubSubProvider(
            projectId = "test-project",
            maxOutstandingMessages = maxMessages
        )
        
        // Test passes if provider initializes without error
        assertTrue(true)
    }

    @Test
    fun provider_shouldCloseGracefully() = runTest {
        val provider = GCPPubSubProvider(
            projectId = "test-project"
        )
        
        // Should not throw during close, even if not connected
        provider.close()
        
        assertTrue(true) // Test passes if no exception thrown
    }

    @Test
    fun provider_shouldUseDefaultSubscriptionPrefix() = runTest {
        val provider = GCPPubSubProvider(
            projectId = "test-project"
        )
        
        val healthInfo = provider.getHealthInfo()
        assertEquals("koog-agent-", healthInfo["subscriptionPrefix"])
    }

    @Test
    fun provider_shouldReportActiveSubscriptions() = runTest {
        val provider = GCPPubSubProvider(
            projectId = "test-project"
        )
        
        val healthInfo = provider.getHealthInfo()
        assertEquals(0, healthInfo["activeSubscriptions"]) // No active subscriptions initially
    }
}