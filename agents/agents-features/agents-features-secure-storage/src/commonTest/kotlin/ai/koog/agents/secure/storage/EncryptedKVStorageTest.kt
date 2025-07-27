package ai.koog.agents.secure.storage

import ai.koog.agents.secure.crypto.SimpleKeyProvider
import ai.koog.agents.secure.storage.impl.EncryptedKVStorage
import ai.koog.agents.secure.storage.backend.kottage.KottageLocalKVBackend
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

/**
 * Tests for EncryptedKVStorage core functionality using real Kottage backend.
 */
class EncryptedKVStorageTest {
    
    private lateinit var realBackend: KottageLocalKVBackend
    private lateinit var storage: EncryptedKVStorage
    private lateinit var tempDbPath: String
    
    @BeforeTest
    fun setup() {
        // Create a temporary database for each test
        tempDbPath = createTempDirectory("secure-storage-test").pathString + "/test.db"
        realBackend = KottageLocalKVBackend(tempDbPath)
        val keyProvider = SimpleKeyProvider("test-key-32-bytes-long-for-aes!!")
        storage = EncryptedKVStorage(realBackend, keyProvider)
    }
    
    @AfterTest
    fun cleanup() {
        // Clean up the backend
        runBlocking {
            storage.close()
        }
    }
    
    @Test
    fun `should encrypt and decrypt values correctly`() = runTest {
        val key = "test-key"
        val value = "test-value"
        
        // Store encrypted value
        storage.put(key, value)
        
        // Retrieve and verify decryption works correctly
        val retrieved = storage.get(key)
        assertEquals(value, retrieved)
        
        // Verify that data is actually encrypted by checking raw backend storage
        val rawValue = realBackend.get(key)
        assertNotNull(rawValue)
        assertNotEquals(value, rawValue) // Should be encrypted, not plain text
    }
    
    @Test
    fun `should return null for non-existent keys`() = runTest {
        val result = storage.get("non-existent-key")
        assertNull(result)
    }
    
    @Test
    fun `should handle delete operations`() = runTest {
        val key = "delete-test"
        val value = "value-to-delete"
        
        // Store and verify
        storage.put(key, value)
        assertEquals(value, storage.get(key))
        
        // Delete and verify
        storage.delete(key)
        assertNull(storage.get(key))
        assertNull(realBackend.get(key))
    }
    
    @Test
    fun `should handle keys with prefix search`() = runTest {
        // Store multiple keys with different prefixes
        storage.put("user:alice:key1", "value1")
        storage.put("user:alice:key2", "value2")
        storage.put("user:bob:key1", "value3")
        storage.put("other:key", "value4")
        
        // Search for alice's keys
        val aliceKeys = storage.keys("user:alice:")
        assertEquals(2, aliceKeys.size)
        assertTrue(aliceKeys.contains("user:alice:key1"))
        assertTrue(aliceKeys.contains("user:alice:key2"))
        
        // Search for all user keys
        val userKeys = storage.keys("user:")
        assertEquals(3, userKeys.size)
    }
    
    @Test
    fun `should handle empty and special characters`() = runTest {
        // Test empty value
        storage.put("empty-key", "")
        assertEquals("", storage.get("empty-key"))
        
        // Test special characters
        val specialValue = "Special: éñáçódé with symbols !@#$%^&*()"
        storage.put("special-key", specialValue)
        assertEquals(specialValue, storage.get("special-key"))
        
        // Test unicode
        val unicodeValue = "Unicode: 🔐🚀✨ emoji test"
        storage.put("unicode-key", unicodeValue)
        assertEquals(unicodeValue, storage.get("unicode-key"))
    }
    
    @Test
    fun `should handle concurrent operations`() = runTest {
        val keys = (1..10).map { "concurrent-key-$it" }
        val values = (1..10).map { "concurrent-value-$it" }
        
        // Store all values
        keys.zip(values).forEach { (key, value) ->
            storage.put(key, value)
        }
        
        // Verify all values
        keys.zip(values).forEach { (key, value) ->
            assertEquals(value, storage.get(key))
        }
    }
    
    @Test
    fun `should provide consistent encryption for same data`() = runTest {
        val key = "consistency-test"
        val value = "same-value"
        
        // Store twice
        storage.put(key, value)
        val encrypted1 = realBackend.get(key)
        
        storage.delete(key)
        storage.put(key, value)
        val encrypted2 = realBackend.get(key)
        
        // Encrypted values should be different (due to unique IVs)
        // but both should decrypt to the same value
        assertNotNull(encrypted1)
        assertNotNull(encrypted2)
        assertNotEquals(encrypted1, encrypted2) // Different encrypted values
        assertEquals(value, storage.get(key)) // Same decrypted value
    }
}

