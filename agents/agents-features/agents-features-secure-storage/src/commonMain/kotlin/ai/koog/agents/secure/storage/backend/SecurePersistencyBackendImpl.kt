package ai.koog.agents.secure.storage.backend

import ai.koog.agents.snapshot.providers.SecurePersistencyBackend
import ai.koog.agents.secure.storage.LocalKVStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementation of SecurePersistencyBackend that bridges the snapshot feature with secure storage.
 * 
 * This adapter allows the snapshot feature to use secure storage without tight coupling
 * by implementing the generic SecurePersistencyBackend interface using LocalKVStorage.
 * 
 * Features:
 * - **Pluggable architecture**: Allows snapshot feature to swap storage backends
 * - **Security transparency**: Encryption/decryption handled by underlying storage
 * - **Thread safety**: Proper synchronization for concurrent access
 * - **Error handling**: Graceful degradation on storage errors
 * 
 * @property storage The underlying secure storage implementation
 */
public class SecurePersistencyBackendImpl(
    private val storage: LocalKVStorage
) : SecurePersistencyBackend {
    
    private val mutex = Mutex()
    
    override suspend fun get(key: String): String? = mutex.withLock {
        try {
            storage.get(key)
        } catch (e: Exception) {
            // Return null on error for graceful degradation
            null
        }
    }
    
    override suspend fun put(key: String, value: String): Unit = mutex.withLock {
        try {
            storage.put(key, value)
        } catch (e: Exception) {
            // Re-throw put errors as they're critical for data integrity
            throw e
        }
    }
    
    override suspend fun delete(key: String): Unit = mutex.withLock {
        try {
            storage.delete(key)
        } catch (e: Exception) {
            // Re-throw delete errors as they may indicate data corruption
            throw e
        }
    }
    
    override suspend fun keys(prefix: String): List<String> = mutex.withLock {
        try {
            storage.keys(prefix)
        } catch (e: Exception) {
            // Return empty list on error for graceful degradation
            emptyList()
        }
    }
    
    override suspend fun close(): Unit = mutex.withLock {
        try {
            storage.close()
        } catch (e: Exception) {
            // Log error but don't fail - cleanup should be best effort
        }
    }
}