package ai.koog.agents.secure.apikeys

import ai.koog.agents.secure.crypto.SimpleKeyProvider
import ai.koog.agents.secure.storage.impl.EncryptedKVStorage
import ai.koog.agents.secure.storage.backend.kottage.KottageLocalKVBackend
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

/**
 * Tests for SecureApiKeyStorage functionality using real Kottage backend.
 */
class SecureApiKeyStorageTest {
    
    private lateinit var realBackend: KottageLocalKVBackend
    private lateinit var encryptedStorage: EncryptedKVStorage
    private lateinit var apiKeyStorage: SecureApiKeyStorage
    private lateinit var tempDbPath: String
    
    @BeforeTest
    fun setup() {
        // Create a temporary database for each test
        tempDbPath = createTempDirectory("api-key-test").pathString + "/test.db"
        realBackend = KottageLocalKVBackend(tempDbPath)
        val keyProvider = SimpleKeyProvider("test-key-32-bytes-long-for-aes!!")
        encryptedStorage = EncryptedKVStorage(realBackend, keyProvider)
        apiKeyStorage = SecureApiKeyStorageImpl(encryptedStorage)
    }
    
    @AfterTest
    fun cleanup() {
        // Clean up the storage
        runBlocking {
            encryptedStorage.close()
        }
    }
    
    @Test
    fun `should store and retrieve API keys without context`() = runTest {
        val service = "openai"
        val apiKey = "sk-test-api-key-123"
        
        // Store API key
        apiKeyStorage.saveApiKey(service, apiKey)
        
        // Retrieve API key
        val retrieved = apiKeyStorage.getApiKey(service)
        assertEquals(apiKey, retrieved)
    }
    
    @Test
    fun `should store and retrieve API keys with context`() = runTest {
        val service = "openai"
        val apiKey = "sk-user-specific-key-456"
        val context = "user:alice"
        
        // Store API key with context
        apiKeyStorage.saveApiKey(service, apiKey, context)
        
        // Retrieve API key with context
        val retrieved = apiKeyStorage.getApiKey(service, context)
        assertEquals(apiKey, retrieved)
        
        // Should not be found without context
        val withoutContext = apiKeyStorage.getApiKey(service)
        assertNull(withoutContext)
    }
    
    @Test
    fun `should isolate API keys by context`() = runTest {
        val service = "openai"
        val aliceKey = "sk-alice-key-123"
        val bobKey = "sk-bob-key-456"
        val aliceContext = "user:alice"
        val bobContext = "user:bob"
        
        // Store keys for different users
        apiKeyStorage.saveApiKey(service, aliceKey, aliceContext)
        apiKeyStorage.saveApiKey(service, bobKey, bobContext)
        
        // Verify isolation
        assertEquals(aliceKey, apiKeyStorage.getApiKey(service, aliceContext))
        assertEquals(bobKey, apiKeyStorage.getApiKey(service, bobContext))
        
        // Cross-contamination check
        assertNotEquals(aliceKey, apiKeyStorage.getApiKey(service, bobContext))
        assertNotEquals(bobKey, apiKeyStorage.getApiKey(service, aliceContext))
    }
    
    @Test
    fun `should delete API keys correctly`() = runTest {
        val service = "github"
        val apiKey = "ghp-test-token-789"
        val context = "user:charlie"
        
        // Store and verify
        apiKeyStorage.saveApiKey(service, apiKey, context)
        assertTrue(apiKeyStorage.hasApiKey(service, context))
        
        // Delete and verify
        val deleted = apiKeyStorage.deleteApiKey(service, context)
        assertTrue(deleted)
        assertFalse(apiKeyStorage.hasApiKey(service, context))
        assertNull(apiKeyStorage.getApiKey(service, context))
        
        // Delete non-existent key
        val deletedAgain = apiKeyStorage.deleteApiKey(service, context)
        assertFalse(deletedAgain)
    }
    
    @Test
    fun `should list services correctly`() = runTest {
        val context = "user:david"
        
        // Store multiple API keys
        apiKeyStorage.saveApiKey("openai", "sk-openai-key", context)
        apiKeyStorage.saveApiKey("anthropic", "sk-ant-key", context)
        apiKeyStorage.saveApiKey("github", "ghp-github-key", context)
        
        // List services
        val services = apiKeyStorage.listServices(context)
        assertEquals(3, services.size)
        assertTrue(services.contains("openai"))
        assertTrue(services.contains("anthropic"))
        assertTrue(services.contains("github"))
    }
    
    @Test
    fun `should list global services separately from context services`() = runTest {
        val context = "user:eve"
        
        // Store global keys
        apiKeyStorage.saveApiKey("openai", "sk-global-openai")
        apiKeyStorage.saveApiKey("google", "google-global-key")
        
        // Store context-specific keys
        apiKeyStorage.saveApiKey("anthropic", "sk-context-anthropic", context)
        apiKeyStorage.saveApiKey("github", "ghp-context-github", context)
        
        // List global services
        val globalServices = apiKeyStorage.listServices()
        assertEquals(2, globalServices.size)
        assertTrue(globalServices.contains("openai"))
        assertTrue(globalServices.contains("google"))
        
        // List context services
        val contextServices = apiKeyStorage.listServices(context)
        assertEquals(2, contextServices.size)
        assertTrue(contextServices.contains("anthropic"))
        assertTrue(contextServices.contains("github"))
    }
    
    @Test
    fun `should rotate API keys correctly`() = runTest {
        val service = "slack"
        val oldKey = "xoxb-old-slack-token"
        val newKey = "xoxb-new-slack-token"
        val context = "user:frank"
        
        // Store initial key
        apiKeyStorage.saveApiKey(service, oldKey, context)
        assertEquals(oldKey, apiKeyStorage.getApiKey(service, context))
        
        // Rotate key
        apiKeyStorage.rotateApiKey(service, newKey, context)
        assertEquals(newKey, apiKeyStorage.getApiKey(service, context))
        
        // Old key should no longer be retrievable
        assertNotEquals(oldKey, apiKeyStorage.getApiKey(service, context))
    }
    
    @Test
    fun `should check key existence correctly`() = runTest {
        val service = "discord"
        val apiKey = "discord-bot-token-123"
        val context = "user:grace"
        
        // Initially should not exist
        assertFalse(apiKeyStorage.hasApiKey(service, context))
        
        // Store key
        apiKeyStorage.saveApiKey(service, apiKey, context)
        assertTrue(apiKeyStorage.hasApiKey(service, context))
        
        // Delete key
        apiKeyStorage.deleteApiKey(service, context)
        assertFalse(apiKeyStorage.hasApiKey(service, context))
    }
    
    @Test
    fun `should handle complex context hierarchies`() = runTest {
        val service = "huggingface"
        val apiKey = "hf-complex-token-789"
        
        // Complex context with multiple levels
        val complexContext = "tenant:acme:dept:engineering:user:henry"
        
        apiKeyStorage.saveApiKey(service, apiKey, complexContext)
        assertEquals(apiKey, apiKeyStorage.getApiKey(service, complexContext))
        
        // Should be isolated from similar contexts
        val differentContext = "tenant:acme:dept:marketing:user:henry"
        assertNull(apiKeyStorage.getApiKey(service, differentContext))
    }
    
    @Test
    fun `should validate input parameters`() = runTest {
        // Test blank service name
        assertFailsWith<IllegalArgumentException> {
            apiKeyStorage.saveApiKey("", "some-key")
        }
        
        assertFailsWith<IllegalArgumentException> {
            apiKeyStorage.saveApiKey("   ", "some-key")
        }
        
        // Test blank API key
        assertFailsWith<IllegalArgumentException> {
            apiKeyStorage.saveApiKey("service", "")
        }
        
        assertFailsWith<IllegalArgumentException> {
            apiKeyStorage.saveApiKey("service", "   ")
        }
    }
    
    @Test
    fun `should handle special characters in contexts and keys`() = runTest {
        val service = "special-service"
        val apiKey = "special-key-with-symbols-!@#$%"
        val context = "user:special-éñáçódé-user"
        
        apiKeyStorage.saveApiKey(service, apiKey, context)
        assertEquals(apiKey, apiKeyStorage.getApiKey(service, context))
    }
}

