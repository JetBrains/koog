package ai.koog.agents.secure.storage.impl

import ai.koog.agents.secure.storage.backend.LocalKVBackend
import ai.koog.agents.secure.storage.LocalKVStorage

/**
 * Plain (unencrypted) implementation of LocalKVStorage that provides direct access to backend storage.
 * 
 * This implementation:
 * - Passes values directly to/from the backend without transformation
 * - Provides the baseline storage functionality
 * - Serves as the foundation for other implementations
 * - Offers maximum performance with no encryption overhead
 * 
 * Use this implementation when:
 * - Data encryption is not required
 * - Maximum performance is needed
 * - Compliance requirements don't mandate encryption
 * - Development/testing environments with non-sensitive data
 * 
 * Usage example:
 * ```kotlin
 * val backend = KottageLocalKVBackend("database.db")
 * val storage = PlainKVStorage(backend)
 * 
 * val memoryProvider = LocalAgentMemoryProvider(
 *     storage = storage,
 *     config = LocalMemoryConfig("memory")
 * )
 * ```
 * 
 * @property backend The underlying storage backend to delegate operations to
 */
public class PlainKVStorage(
    private val backend: LocalKVBackend
) : LocalKVStorage {

    /**
     * Retrieves a value directly from the backend storage.
     * 
     * @param key The key to retrieve
     * @return Value from backend or null if key doesn't exist
     * @throws StorageException if backend operation fails
     */
    override suspend fun get(key: String): String? {
        return backend.get(key)
    }

    /**
     * Stores a value directly in the backend storage.
     * 
     * @param key The key to store under
     * @param value The value to store
     * @throws StorageException if backend operation fails
     */
    override suspend fun put(key: String, value: String) {
        backend.put(key, value)
    }

    /**
     * Deletes a key directly from the backend storage.
     * 
     * @param key The key to delete
     * @throws StorageException if backend operation fails
     */
    override suspend fun delete(key: String) {
        backend.delete(key)
    }

    /**
     * Lists keys matching a prefix directly from the backend storage.
     * 
     * @param prefix Key prefix to match
     * @return List of matching keys from backend
     * @throws StorageException if backend operation fails
     */
    override suspend fun keys(prefix: String): List<String> {
        return backend.keys(prefix)
    }

    /**
     * Closes the backend storage connection.
     */
    override suspend fun close() {
        backend.close()
    }
}