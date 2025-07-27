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
 * Tests for ApiKeyResolver hierarchical fallback functionality using real storage.
 */
class ApiKeyResolverTest {
    
    private lateinit var realBackend: KottageLocalKVBackend
    private lateinit var encryptedStorage: EncryptedKVStorage
    private lateinit var apiKeyStorage: SecureApiKeyStorage
    private lateinit var mockEnvProvider: MockEnvironmentApiKeyProvider
    private lateinit var resolver: ApiKeyResolver
    private lateinit var tempDbPath: String
    
    @BeforeTest
    fun setup() {
        // Create real storage backend
        tempDbPath = createTempDirectory("resolver-test").pathString + "/test.db"
        realBackend = KottageLocalKVBackend(tempDbPath)
        val keyProvider = SimpleKeyProvider("test-key-32-bytes-long-for-aes!!")
        encryptedStorage = EncryptedKVStorage(realBackend, keyProvider)
        apiKeyStorage = SecureApiKeyStorageImpl(encryptedStorage)
        
        // Create mock environment provider for testing fallback
        mockEnvProvider = MockEnvironmentApiKeyProvider()
        resolver = ApiKeyResolver(apiKeyStorage, mockEnvProvider)
    }
    
    @AfterTest
    fun cleanup() {
        runBlocking {
            encryptedStorage.close()
        }
    }
    
    @Test
    fun `should resolve from user and agent context first`() = runTest {
        val service = "openai"
        val userContext = "user:alice"
        val agentContext = "agent:assistant"
        val expectedKey = "sk-user-agent-specific-key"
        
        // Set up keys at different levels using real storage
        apiKeyStorage.saveApiKey(service, expectedKey, "$userContext:$agentContext")
        apiKeyStorage.saveApiKey(service, "sk-user-only-key", userContext)
        apiKeyStorage.saveApiKey(service, "sk-agent-only-key", agentContext)
        apiKeyStorage.saveApiKey(service, "sk-global-key")
        
        val resolved = resolver.resolveApiKey(service, userContext, agentContext)
        assertEquals(expectedKey, resolved)
        
        val source = resolver.getApiKeySource(service, userContext, agentContext)
        assertTrue(source is ApiKeySource.UserAgent)
        assertEquals(userContext, source.userContext)
        assertEquals(agentContext, source.agentContext)
    }
    
    @Test
    fun `should fallback to user context when user-agent not found`() = runTest {
        val service = "anthropic"
        val userContext = "user:bob"
        val agentContext = "agent:analyzer"
        val expectedKey = "sk-user-only-key"
        
        // No user+agent key, but user key exists
        apiKeyStorage.saveApiKey(service, expectedKey, userContext)
        apiKeyStorage.saveApiKey(service, "sk-agent-only-key", agentContext)
        apiKeyStorage.saveApiKey(service, "sk-global-key")
        mockEnvProvider.keys[service] = "sk-env-key"
        
        val resolved = resolver.resolveApiKey(service, userContext, agentContext)
        assertEquals(expectedKey, resolved)
        
        val source = resolver.getApiKeySource(service, userContext, agentContext)
        assertTrue(source is ApiKeySource.User)
        assertEquals(userContext, source.userContext)
    }
    
    @Test
    fun `should fallback to agent context when user not found`() = runTest {
        val service = "github"
        val userContext = "user:charlie"
        val agentContext = "agent:coder"
        val expectedKey = "sk-agent-only-key"
        
        // No user keys, but agent key exists
        apiKeyStorage.saveApiKey(service, expectedKey, agentContext)
        apiKeyStorage.saveApiKey(service, "sk-global-key")
        
        val resolved = resolver.resolveApiKey(service, userContext, agentContext)
        assertEquals(expectedKey, resolved)
        
        val source = resolver.getApiKeySource(service, userContext, agentContext)
        assertTrue(source is ApiKeySource.Agent)
        assertEquals(agentContext, source.agentContext)
    }
    
    @Test
    fun `should fallback to global when no context keys found`() = runTest {
        val service = "slack"
        val userContext = "user:diana"
        val agentContext = "agent:bot"
        val expectedKey = "sk-global-key"
        
        // Only global key exists
        apiKeyStorage.saveApiKey(service, expectedKey)
        mockEnvProvider.keys[service] = "sk-env-key"
        
        val resolved = resolver.resolveApiKey(service, userContext, agentContext)
        assertEquals(expectedKey, resolved)
        
        val source = resolver.getApiKeySource(service, userContext, agentContext)
        assertTrue(source is ApiKeySource.Global)
    }
    
    @Test
    fun `should fallback to environment when no storage keys found`() = runTest {
        val service = "discord"
        val userContext = "user:eve"
        val agentContext = "agent:moderator"
        val expectedKey = "sk-env-key"
        
        // Only environment key exists
        mockEnvProvider.keys[service] = expectedKey
        
        val resolved = resolver.resolveApiKey(service, userContext, agentContext)
        assertEquals(expectedKey, resolved)
        
        val source = resolver.getApiKeySource(service, userContext, agentContext)
        assertTrue(source is ApiKeySource.Environment)
    }
    
    @Test
    fun `should return null when no keys found anywhere`() = runTest {
        val service = "unknown-service"
        val userContext = "user:frank"
        val agentContext = "agent:helper"
        
        val resolved = resolver.resolveApiKey(service, userContext, agentContext)
        assertNull(resolved)
        
        val source = resolver.getApiKeySource(service, userContext, agentContext)
        assertNull(source)
    }
    
    @Test
    fun `should handle missing contexts gracefully`() = runTest {
        val service = "google"
        val expectedKey = "sk-global-key"
        
        apiKeyStorage.saveApiKey(service, expectedKey)
        
        // No contexts provided
        val resolved = resolver.resolveApiKey(service)
        assertEquals(expectedKey, resolved)
        
        val source = resolver.getApiKeySource(service)
        assertTrue(source is ApiKeySource.Global)
    }
    
    @Test
    fun `should check key availability correctly`() = runTest {
        val service = "huggingface"
        val userContext = "user:grace"
        
        // No keys initially
        assertFalse(resolver.hasApiKey(service, userContext))
        
        // Add user key
        apiKeyStorage.saveApiKey(service, "hf-token", userContext)
        assertTrue(resolver.hasApiKey(service, userContext))
        
        // Check different service
        assertFalse(resolver.hasApiKey("other-service", userContext))
        
        // Check environment fallback
        mockEnvProvider.keys["env-service"] = "env-token"
        assertTrue(resolver.hasApiKey("env-service"))
    }
    
    @Test
    fun `should handle complex context combinations`() = runTest {
        val service = "complex-service"
        val userContext = "tenant:acme:user:henry"
        val agentContext = "dept:engineering:agent:reviewer"
        val expectedKey = "sk-complex-context-key"
        
        apiKeyStorage.saveApiKey(service, expectedKey, "$userContext:$agentContext")
        
        val resolved = resolver.resolveApiKey(service, userContext, agentContext)
        assertEquals(expectedKey, resolved)
        
        val source = resolver.getApiKeySource(service, userContext, agentContext)
        assertTrue(source is ApiKeySource.UserAgent)
    }
    
    @Test
    fun `should test toString methods of ApiKeySource classes`() {
        val userAgent = ApiKeySource.UserAgent("user:alice", "agent:bot")
        assertTrue(userAgent.toString().contains("User+Agent"))
        assertTrue(userAgent.toString().contains("user:alice"))
        assertTrue(userAgent.toString().contains("agent:bot"))
        
        val user = ApiKeySource.User("user:bob")
        assertTrue(user.toString().contains("User(user:bob)"))
        
        val agent = ApiKeySource.Agent("agent:helper")
        assertTrue(agent.toString().contains("Agent(agent:helper)"))
        
        val global = ApiKeySource.Global
        assertEquals("Global", global.toString())
        
        val environment = ApiKeySource.Environment
        assertEquals("Environment", environment.toString())
    }
}


/**
 * Test implementation of EnvironmentApiKeyProvider for testing.
 */
private class MockEnvironmentApiKeyProvider : EnvironmentApiKeyProvider() {
    val keys = mutableMapOf<String, String>()
    
    override fun getApiKey(service: String): String? {
        return keys[service] ?: super.getApiKey(service)
    }
    
    override fun hasApiKey(service: String): Boolean {
        return keys.containsKey(service) || super.hasApiKey(service)
    }
}