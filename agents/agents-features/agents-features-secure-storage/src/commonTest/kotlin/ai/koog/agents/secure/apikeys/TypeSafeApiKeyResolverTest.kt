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
 * Tests for type-safe API key resolver extension functions.
 */
class TypeSafeApiKeyResolverTest {
    
    private lateinit var realBackend: KottageLocalKVBackend
    private lateinit var encryptedStorage: EncryptedKVStorage
    private lateinit var apiKeyStorage: SecureApiKeyStorage
    private lateinit var mockEnvProvider: MockEnvironmentApiKeyProvider
    private lateinit var resolver: ApiKeyResolver
    private lateinit var tempDbPath: String
    
    @BeforeTest
    fun setup() {
        tempDbPath = createTempDirectory("typesafe-resolver-test").pathString + "/test.db"
        realBackend = KottageLocalKVBackend(tempDbPath)
        val keyProvider = SimpleKeyProvider("test-key-32-bytes-long-for-aes!!")
        encryptedStorage = EncryptedKVStorage(realBackend, keyProvider)
        apiKeyStorage = SecureApiKeyStorageImpl(encryptedStorage)
        
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
    fun `should resolve API keys using providers`() = runTest {
        val userContext = "user:alice"
        val agentContext = "agent:assistant"
        val openAIKey = "sk-1234567890abcdefghijklmnopqr"
        
        // Store key using type-safe API
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, openAIKey, userContext)
        
        // Resolve using type-safe API
        val resolved = resolver.resolveApiKey(ApiKeyProviders.OpenAI, userContext, agentContext)
        assertEquals(openAIKey, resolved)
        
        // Get source information
        val source = resolver.getApiKeySource(ApiKeyProviders.OpenAI, userContext, agentContext)
        assertTrue(source is ApiKeySource.User)
        assertEquals(userContext, source.userContext)
    }
    
    @Test
    fun `should resolve multiple API keys in batch`() = runTest {
        val userContext = "user:bob"
        val providers = listOf(ApiKeyProviders.OpenAI, ApiKeyProviders.Anthropic, ApiKeyProviders.GitHub)
        
        // Store some keys
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-openai-key", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.GitHub, "ghp_github-key", userContext)
        // Note: Anthropic key not stored
        
        val resolved = resolver.resolveApiKeys(providers, userContext)
        
        assertEquals("sk-openai-key", resolved[ApiKeyProviders.OpenAI])
        assertNull(resolved[ApiKeyProviders.Anthropic])
        assertEquals("ghp_github-key", resolved[ApiKeyProviders.GitHub])
    }
    
    @Test
    fun `should resolve only available API keys`() = runTest {
        val userContext = "user:charlie"
        val providers = listOf(ApiKeyProviders.OpenAI, ApiKeyProviders.Anthropic, ApiKeyProviders.AWS)
        
        // Store some keys
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-openai-key", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.AWS, "AKIA1234567890ABCDEF", userContext)
        // Note: Anthropic key not stored
        
        val available = resolver.resolveAvailableApiKeys(providers, userContext)
        
        assertEquals(2, available.size)
        assertEquals("sk-openai-key", available[ApiKeyProviders.OpenAI])
        assertEquals("AKIA1234567890ABCDEF", available[ApiKeyProviders.AWS])
        assertFalse(available.containsKey(ApiKeyProviders.Anthropic))
    }
    
    @Test
    fun `should resolve LLM provider keys`() = runTest {
        val userContext = "user:diana"
        
        // Store LLM provider keys
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-openai-key", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.Anthropic, "sk-ant-key", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.Cohere, "cohere-key", userContext)
        // Note: HuggingFace and GoogleAI keys not stored
        
        val llmKeys = resolver.resolveLLMProviderKeys(userContext)
        
        assertEquals("sk-openai-key", llmKeys.openAI)
        assertEquals("sk-ant-key", llmKeys.anthropic)
        assertEquals("cohere-key", llmKeys.cohere)
        assertNull(llmKeys.huggingFace)
        assertNull(llmKeys.googleAI)
        
        // Test helper methods
        assertEquals("sk-openai-key", llmKeys.getFirstAvailable())
        
        val available = llmKeys.getAvailable()
        assertEquals(3, available.size)
        assertTrue(available.containsKey(ApiKeyProviders.OpenAI))
        assertTrue(available.containsKey(ApiKeyProviders.Anthropic))
        assertTrue(available.containsKey(ApiKeyProviders.Cohere))
    }
    
    @Test
    fun `should resolve cloud provider keys`() = runTest {
        val userContext = "user:eve"
        
        // Store cloud provider keys
        apiKeyStorage.saveApiKey(ApiKeyProviders.AWS, "AKIA1234567890ABCDEF", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.GoogleCloud, "google-cloud-key", userContext)
        // Note: Azure key not stored
        
        val cloudKeys = resolver.resolveCloudProviderKeys(userContext)
        
        assertEquals("AKIA1234567890ABCDEF", cloudKeys.aws)
        assertEquals("google-cloud-key", cloudKeys.googleCloud)
        assertNull(cloudKeys.azure)
        
        // Test helper methods
        assertEquals("AKIA1234567890ABCDEF", cloudKeys.getFirstAvailable())
        
        val available = cloudKeys.getAvailable()
        assertEquals(2, available.size)
        assertTrue(available.containsKey(ApiKeyProviders.AWS))
        assertTrue(available.containsKey(ApiKeyProviders.GoogleCloud))
    }
    
    @Test
    fun `should use convenience methods for specific providers`() = runTest {
        val userContext = "user:frank"
        val agentContext = "agent:assistant"
        
        // Store keys
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-openai-convenience", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.Anthropic, "sk-ant-convenience", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.AWS, "AKIA-convenience", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.Stripe, "sk_test_convenience", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.GitHub, "ghp_convenience", userContext)
        
        // Test convenience methods
        assertEquals("sk-openai-convenience", resolver.resolveOpenAIKey(userContext, agentContext))
        assertEquals("sk-ant-convenience", resolver.resolveAnthropicKey(userContext, agentContext))
        assertEquals("AKIA-convenience", resolver.resolveAWSKey(userContext, agentContext))
        assertEquals("sk_test_convenience", resolver.resolveStripeKey(userContext, agentContext))
        assertEquals("ghp_convenience", resolver.resolveGitHubKey(userContext, agentContext))
    }
    
    @Test
    fun `should work with context-scoped resolver`() = runTest {
        val userContext = "user:grace"
        val agentContext = "agent:helper"
        val scopedResolver = resolver.scoped(userContext, agentContext)
        
        // Store key
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-scoped-key", userContext)
        
        // Resolve using scoped resolver
        assertEquals("sk-scoped-key", scopedResolver.resolveApiKey(ApiKeyProviders.OpenAI))
        
        val source = scopedResolver.getApiKeySource(ApiKeyProviders.OpenAI)
        assertTrue(source is ApiKeySource.User)
        
        // Test batch operations
        val providers = listOf(ApiKeyProviders.OpenAI, ApiKeyProviders.Anthropic)
        val available = scopedResolver.resolveAvailableApiKeys(providers)
        assertEquals(1, available.size)
        assertEquals("sk-scoped-key", available[ApiKeyProviders.OpenAI])
        
        // Test convenience methods
        assertEquals("sk-scoped-key", scopedResolver.resolveOpenAIKey())
        assertNull(scopedResolver.resolveAnthropicKey())
    }
    
    @Test
    fun `should use resolution builder for complex scenarios`() = runTest {
        val userContext = "user:henry"
        val agentContext = "agent:complex"
        
        // Store required and optional keys
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-required-key", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.GitHub, "ghp-optional-key", userContext)
        // Note: Anthropic (required) and AWS (optional) keys not stored
        
        val result = resolver.buildResolution()
            .forUser(userContext)
            .forAgent(agentContext)
            .require(ApiKeyProviders.OpenAI, ApiKeyProviders.Anthropic)
            .optional(ApiKeyProviders.GitHub, ApiKeyProviders.AWS)
            .resolve()
        
        assertFalse(result.isComplete)
        assertEquals(1, result.missing.size)
        assertTrue(result.missing.contains(ApiKeyProviders.Anthropic))
        
        assertEquals(2, result.resolved.size)
        assertEquals("sk-required-key", result.resolved[ApiKeyProviders.OpenAI])
        assertEquals("ghp-optional-key", result.resolved[ApiKeyProviders.GitHub])
        
        // Test requirement validation
        assertFailsWith<MissingApiKeysException> {
            result.requireComplete()
        }
    }
    
    @Test
    fun `should handle complete resolution scenarios`() = runTest {
        val userContext = "user:iris"
        
        // Store all required keys
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-complete-key", userContext)
        apiKeyStorage.saveApiKey(ApiKeyProviders.Anthropic, "sk-ant-complete", userContext)
        
        val result = resolver.buildResolution()
            .forUser(userContext)
            .require(ApiKeyProviders.OpenAI, ApiKeyProviders.Anthropic)
            .optional(ApiKeyProviders.GitHub)
            .resolve()
        
        assertTrue(result.isComplete)
        assertEquals(0, result.missing.size)
        assertEquals(2, result.resolved.size)
        
        // Should not throw
        val completed = result.requireComplete()
        assertEquals(result, completed)
    }
    
    @Test
    fun `should fallback to environment in type-safe resolution`() = runTest {
        val userContext = "user:jack"
        
        // No stored keys, but environment key available
        mockEnvProvider.keys["openai"] = "sk-env-openai-key"
        
        val resolved = resolver.resolveApiKey(ApiKeyProviders.OpenAI, userContext)
        assertEquals("sk-env-openai-key", resolved)
        
        val source = resolver.getApiKeySource(ApiKeyProviders.OpenAI, userContext)
        assertTrue(source is ApiKeySource.Environment)
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