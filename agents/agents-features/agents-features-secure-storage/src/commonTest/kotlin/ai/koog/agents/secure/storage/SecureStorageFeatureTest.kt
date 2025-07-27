package ai.koog.agents.secure.storage

import ai.koog.agents.secure.crypto.SimpleKeyProvider
import ai.koog.agents.secure.apikeys.ApiKeySource
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

/**
 * Tests for SecureStorage feature integration and API key management using real storage.
 */
class SecureStorageFeatureTest {
    
    private lateinit var tempDbPath: String
    
    @BeforeTest
    fun setup() {
        tempDbPath = createTempDirectory("feature-test").pathString + "/test.db"
    }
    
    @Test
    fun `should configure encrypted mode correctly`() {
        val config = SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = SimpleKeyProvider("test-key-32-bytes-long-for-aes!!"),
                databasePath = tempDbPath
            )
        }
        
        val secureStorage = SecureStorage(config)
        
        // Should be able to access API key features in encrypted mode
        assertNotNull(secureStorage.apiKeys())
        assertNotNull(secureStorage.apiKeyResolver())
    }
    
    @Test
    fun `should prevent API key usage in plain mode`() {
        val config = SecureStorageConfig().apply {
            mode = PlainMode(
                databasePath = tempDbPath + "-plain",
                suppressSecurityWarning = true
            )
        }
        
        val secureStorage = SecureStorage(config)
        
        // Should throw IllegalStateException when trying to use API key features
        assertFailsWith<IllegalStateException> {
            secureStorage.apiKeys()
        }
        
        assertFailsWith<IllegalStateException> {
            secureStorage.apiKeyResolver()
        }
    }
    
    @Test
    fun `should provide context-scoped API key storage`() = runTest {
        val config = SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = SimpleKeyProvider("test-key-32-bytes-long-for-aes!!"),
                databasePath = tempDbPath + "-context"
            )
        }
        
        val secureStorage = SecureStorage(config)
        val userContext = "user:integration-test"
        val scopedStorage = secureStorage.apiKeys(userContext)
        
        // Store key via scoped storage (context should be automatically applied)
        scopedStorage.saveApiKey("test-service", "test-key")
        
        // Should be retrievable via scoped storage
        assertEquals("test-key", scopedStorage.getApiKey("test-service"))
        
        // Should also be retrievable via base storage with explicit context
        assertEquals("test-key", secureStorage.apiKeys().getApiKey("test-service", userContext))
        
        // Should not be retrievable without context
        assertNull(secureStorage.apiKeys().getApiKey("test-service"))
    }
    
    @Test
    fun `should integrate API key resolver with storage`() = runTest {
        val config = SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = SimpleKeyProvider("test-key-32-bytes-long-for-aes!!"),
                databasePath = tempDbPath + "-resolver"
            )
        }
        
        val secureStorage = SecureStorage(config)
        val resolver = secureStorage.apiKeyResolver()
        
        // Store keys at different levels
        secureStorage.apiKeys().saveApiKey("service", "global-key")
        secureStorage.apiKeys().saveApiKey("service", "user-key", "user:test")
        secureStorage.apiKeys().saveApiKey("service", "agent-key", "agent:test")
        secureStorage.apiKeys().saveApiKey("service", "combined-key", "user:test:agent:test")
        
        // Test hierarchical resolution
        assertEquals("combined-key", resolver.resolveApiKey("service", "user:test", "agent:test"))
        assertEquals("user-key", resolver.resolveApiKey("service", "user:test"))
        assertEquals("agent-key", resolver.resolveApiKey("service", agentContext = "agent:test"))
        assertEquals("global-key", resolver.resolveApiKey("service"))
        
        // Test source identification
        val source = resolver.getApiKeySource("service", "user:test", "agent:test")
        assertTrue(source is ApiKeySource.UserAgent)
    }
    
    @Test
    fun `should handle plain mode security warning`() {
        // Capture security warning output (in real implementation)
        val config = SecureStorageConfig().apply {
            mode = PlainMode(
                databasePath = tempDbPath + "-warning",
                suppressSecurityWarning = false  // Should trigger warning
            )
        }
        
        // Warning should be emitted during initialization
        val secureStorage = SecureStorage(config)
        assertNotNull(secureStorage) // Just verify it constructs
    }
    
    @Test
    fun `should suppress security warning when requested`() {
        val config = SecureStorageConfig().apply {
            mode = PlainMode(
                databasePath = tempDbPath + "-suppressed",
                suppressSecurityWarning = true  // Should suppress warning
            )
        }
        
        // No warning should be emitted
        val secureStorage = SecureStorage(config)
        assertNotNull(secureStorage)
    }
    
    @Test
    fun `should validate encrypted mode configuration`() {
        val config = SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = null,  // Invalid - should cause error
                databasePath = tempDbPath + "-invalid"
            )
        }
        
        val secureStorage = SecureStorage(config)
        
        // Should fail when trying to access storage due to missing key provider
        assertFailsWith<IllegalStateException> {
            secureStorage.apiKeys()
        }
    }
    
    @Test
    fun `should use default configuration correctly`() {
        val config = SecureStorageConfig()  // Default configuration
        
        // Should default to PlainMode
        assertTrue(config.mode is PlainMode)
        
        val plainMode = config.mode as PlainMode
        assertEquals("koog-plain.db", plainMode.databasePath)
        assertFalse(plainMode.suppressSecurityWarning)
    }
}

/**
 * Tests for configuration classes.
 */
class ConfigurationTest {
    
    @Test
    fun `should create default SecureStorageConfig`() {
        val config = SecureStorageConfig()
        
        assertTrue(config.mode is PlainMode)
        val plainMode = config.mode as PlainMode
        assertEquals("koog-plain.db", plainMode.databasePath)
        assertFalse(plainMode.suppressSecurityWarning)
    }
    
    @Test
    fun `should configure EncryptedMode correctly`() {
        val keyProvider = SimpleKeyProvider("test-key-32-bytes-long-for-aes!!")
        val encryptedMode = EncryptedMode(
            keyProvider = keyProvider,
            databasePath = "secure.db"
        )
        
        assertEquals(keyProvider, encryptedMode.keyProvider)
        assertEquals("secure.db", encryptedMode.databasePath)
    }
    
    @Test
    fun `should configure PlainMode correctly`() {
        val plainMode = PlainMode(
            databasePath = "plain.db",
            suppressSecurityWarning = true
        )
        
        assertEquals("plain.db", plainMode.databasePath)
        assertTrue(plainMode.suppressSecurityWarning)
    }
    
    @Test
    fun `should use default values for EncryptedMode`() {
        val encryptedMode = EncryptedMode()
        
        assertNull(encryptedMode.keyProvider)
        assertEquals("koog-secure.db", encryptedMode.databasePath)
    }
    
    @Test
    fun `should use default values for PlainMode`() {
        val plainMode = PlainMode()
        
        assertEquals("koog-plain.db", plainMode.databasePath)
        assertFalse(plainMode.suppressSecurityWarning)
    }
}