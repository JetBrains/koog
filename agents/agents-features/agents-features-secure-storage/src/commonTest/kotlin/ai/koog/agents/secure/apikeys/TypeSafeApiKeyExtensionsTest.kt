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
 * Tests for type-safe API key extension functions that ensure compile-time safety
 * and automatic validation.
 */
class TypeSafeApiKeyExtensionsTest {
    
    private lateinit var realBackend: KottageLocalKVBackend
    private lateinit var encryptedStorage: EncryptedKVStorage
    private lateinit var apiKeyStorage: SecureApiKeyStorage
    private lateinit var tempDbPath: String
    
    @BeforeTest
    fun setup() {
        tempDbPath = createTempDirectory("typesafe-test").pathString + "/test.db"
        realBackend = KottageLocalKVBackend(tempDbPath)
        val keyProvider = SimpleKeyProvider("test-key-32-bytes-long-for-aes!!")
        encryptedStorage = EncryptedKVStorage(realBackend, keyProvider)
        apiKeyStorage = SecureApiKeyStorageImpl(encryptedStorage)
    }
    
    @AfterTest
    fun cleanup() {
        runBlocking {
            encryptedStorage.close()
        }
    }
    
    @Test
    fun `should save and retrieve API keys using providers`() = runTest {
        val openAIKey = "sk-1234567890abcdefghijklmnopqr"
        val anthropicKey = "sk-ant-api03-abcdefghijklmnopqrstuvwxyz"
        val context = "user:alice"
        
        // Save using type-safe API
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, openAIKey, context)
        apiKeyStorage.saveApiKey(ApiKeyProviders.Anthropic, anthropicKey, context)
        
        // Retrieve using type-safe API
        val retrievedOpenAI = apiKeyStorage.getApiKey(ApiKeyProviders.OpenAI, context)
        val retrievedAnthropic = apiKeyStorage.getApiKey(ApiKeyProviders.Anthropic, context)
        
        assertEquals(openAIKey, retrievedOpenAI)
        assertEquals(anthropicKey, retrievedAnthropic)
    }
    
    @Test
    fun `should validate API key formats when enabled`() = runTest {
        val validOpenAIKey = "sk-1234567890abcdefghijklmnopqr"
        val invalidOpenAIKey = "invalid-openai-key"
        
        // Valid key should work
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, validOpenAIKey, validateFormat = true)
        assertEquals(validOpenAIKey, apiKeyStorage.getApiKey(ApiKeyProviders.OpenAI))
        
        // Invalid key should throw exception
        assertFailsWith<InvalidApiKeyFormatException> {
            apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, invalidOpenAIKey, validateFormat = true)
        }
    }
    
    @Test
    fun `should skip validation when disabled`() = runTest {
        val invalidOpenAIKey = "invalid-openai-key"
        
        // Should work when validation is disabled
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, invalidOpenAIKey, validateFormat = false)
        assertEquals(invalidOpenAIKey, apiKeyStorage.getApiKey(ApiKeyProviders.OpenAI))
    }
    
    @Test
    fun `should check key existence using providers`() = runTest {
        val context = "user:bob"
        
        assertFalse(apiKeyStorage.hasApiKey(ApiKeyProviders.GitHub, context))
        
        apiKeyStorage.saveApiKey(ApiKeyProviders.GitHub, "ghp_1234567890abcdefghijklmnopqrstuvwxyz", context)
        
        assertTrue(apiKeyStorage.hasApiKey(ApiKeyProviders.GitHub, context))
    }
    
    @Test
    fun `should remove API keys using providers`() = runTest {
        val context = "user:charlie"
        val stripeKey = "sk_test_1234567890abcdefghijklmnopqr"
        
        // Store key
        apiKeyStorage.saveApiKey(ApiKeyProviders.Stripe, stripeKey, context)
        assertTrue(apiKeyStorage.hasApiKey(ApiKeyProviders.Stripe, context))
        
        // Remove key
        val removed = apiKeyStorage.removeApiKey(ApiKeyProviders.Stripe, context)
        assertTrue(removed)
        assertFalse(apiKeyStorage.hasApiKey(ApiKeyProviders.Stripe, context))
        
        // Try to remove again
        val removedAgain = apiKeyStorage.removeApiKey(ApiKeyProviders.Stripe, context)
        assertFalse(removedAgain)
    }
    
    @Test
    fun `should rotate API keys with validation`() = runTest {
        val context = "user:diana"
        val oldKey = "sk-1234567890abcdefghijklmnopqr"
        val newKey = "sk-abcdefghijklmnopqrstuvwxyz12"
        val invalidKey = "invalid-key"
        
        // Store initial key
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, oldKey, context)
        assertEquals(oldKey, apiKeyStorage.getApiKey(ApiKeyProviders.OpenAI, context))
        
        // Rotate to valid key
        apiKeyStorage.rotateApiKey(ApiKeyProviders.OpenAI, newKey, context, validateFormat = true)
        assertEquals(newKey, apiKeyStorage.getApiKey(ApiKeyProviders.OpenAI, context))
        
        // Try to rotate to invalid key
        assertFailsWith<InvalidApiKeyFormatException> {
            apiKeyStorage.rotateApiKey(ApiKeyProviders.OpenAI, invalidKey, context, validateFormat = true)
        }
        
        // Key should still be the valid new key
        assertEquals(newKey, apiKeyStorage.getApiKey(ApiKeyProviders.OpenAI, context))
    }
    
    @Test
    fun `should save multiple API keys in batch`() = runTest {
        val context = "user:eve"
        val keys = mapOf(
            ApiKeyProviders.OpenAI to "sk-1234567890abcdefghijklmnopqr",
            ApiKeyProviders.Anthropic to "sk-ant-api03-abcdefghijklmnopqrstuvwxyz",
            ApiKeyProviders.GitHub to "ghp_1234567890abcdefghijklmnopqrstuvwxyz"
        )
        
        // Save all keys in batch
        apiKeyStorage.saveApiKeys(keys, context)
        
        // Verify all keys were saved
        for ((provider, expectedKey) in keys) {
            assertEquals(expectedKey, apiKeyStorage.getApiKey(provider, context))
        }
    }
    
    @Test
    fun `should get multiple API keys for providers`() = runTest {
        val context = "user:frank"
        val providers = listOf(ApiKeyProviders.OpenAI, ApiKeyProviders.Anthropic, ApiKeyProviders.AWS)
        
        // Store some keys
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-openai-key", context)
        apiKeyStorage.saveApiKey(ApiKeyProviders.AWS, "AKIA1234567890ABCDEF", context)
        // Note: Anthropic key not stored
        
        val result = apiKeyStorage.getApiKeys(providers, context)
        
        assertEquals("sk-openai-key", result[ApiKeyProviders.OpenAI])
        assertNull(result[ApiKeyProviders.Anthropic])
        assertEquals("AKIA1234567890ABCDEF", result[ApiKeyProviders.AWS])
    }
    
    @Test
    fun `should get only available API keys`() = runTest {
        val context = "user:grace"
        val providers = listOf(ApiKeyProviders.OpenAI, ApiKeyProviders.Anthropic, ApiKeyProviders.AWS)
        
        // Store some keys
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-openai-key", context)
        apiKeyStorage.saveApiKey(ApiKeyProviders.AWS, "AKIA1234567890ABCDEF", context)
        // Note: Anthropic key not stored
        
        val available = apiKeyStorage.getAvailableApiKeys(providers, context)
        
        assertEquals(2, available.size)
        assertEquals("sk-openai-key", available[ApiKeyProviders.OpenAI])
        assertEquals("AKIA1234567890ABCDEF", available[ApiKeyProviders.AWS])
        assertFalse(available.containsKey(ApiKeyProviders.Anthropic))
    }
    
    @Test
    fun `should list providers with stored keys`() = runTest {
        val context = "user:henry"
        
        // Store keys for some providers
        apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-openai-key", context)
        apiKeyStorage.saveApiKey(ApiKeyProviders.GitHub, "ghp_github-key", context)
        
        val providersWithKeys = apiKeyStorage.getProvidersWithKeys(context)
        
        assertEquals(2, providersWithKeys.size)
        assertTrue(providersWithKeys.contains(ApiKeyProviders.OpenAI))
        assertTrue(providersWithKeys.contains(ApiKeyProviders.GitHub))
        assertFalse(providersWithKeys.contains(ApiKeyProviders.Anthropic))
    }
    
    @Test
    fun `should use convenience extensions for specific providers`() = runTest {
        val context = "user:iris"
        
        // Test OpenAI convenience methods
        apiKeyStorage.saveOpenAIKey("sk-openai-convenience", context)
        assertEquals("sk-openai-convenience", apiKeyStorage.getOpenAIKey(context))
        
        // Test Anthropic convenience methods
        apiKeyStorage.saveAnthropicKey("sk-ant-api03-convenience", context)
        assertEquals("sk-ant-api03-convenience", apiKeyStorage.getAnthropicKey(context))
        
        // Test AWS convenience methods
        apiKeyStorage.saveAWSKey("AKIA-convenience", context)
        assertEquals("AKIA-convenience", apiKeyStorage.getAWSKey(context))
        
        // Test Stripe convenience methods
        apiKeyStorage.saveStripeKey("sk_test_convenience", context)
        assertEquals("sk_test_convenience", apiKeyStorage.getStripeKey(context))
        
        // Test GitHub convenience methods
        apiKeyStorage.saveGitHubKey("ghp_convenience", context)
        assertEquals("ghp_convenience", apiKeyStorage.getGitHubKey(context))
    }
    
    @Test
    fun `should work with context-scoped storage`() = runTest {
        val context = "user:jack"
        val scopedStorage = apiKeyStorage.scoped(context)
        
        // Save and retrieve using scoped storage
        scopedStorage.saveApiKey(ApiKeyProviders.OpenAI, "sk-scoped-key")
        assertEquals("sk-scoped-key", scopedStorage.getApiKey(ApiKeyProviders.OpenAI))
        
        // Should be isolated to the context
        assertNull(apiKeyStorage.getApiKey(ApiKeyProviders.OpenAI))
        assertEquals("sk-scoped-key", apiKeyStorage.getApiKey(ApiKeyProviders.OpenAI, context))
        
        // Test other scoped operations
        assertTrue(scopedStorage.hasApiKey(ApiKeyProviders.OpenAI))
        
        val removed = scopedStorage.removeApiKey(ApiKeyProviders.OpenAI)
        assertTrue(removed)
        assertFalse(scopedStorage.hasApiKey(ApiKeyProviders.OpenAI))
    }
    
    @Test
    fun `should validate exception contains provider information`() = runTest {
        val invalidKey = "invalid-key"
        
        val exception = assertFailsWith<InvalidApiKeyFormatException> {
            apiKeyStorage.saveApiKey(ApiKeyProviders.OpenAI, invalidKey, validateFormat = true)
        }
        
        assertTrue(exception.message!!.contains("OpenAI"))
        assertTrue(exception.message!!.contains("sk-"))
        assertEquals(ApiKeyProviders.OpenAI, exception.provider)
        assertEquals(invalidKey, exception.providedKey)
    }
}