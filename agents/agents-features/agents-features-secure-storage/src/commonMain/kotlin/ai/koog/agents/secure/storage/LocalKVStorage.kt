package ai.koog.agents.secure.storage

/**
 * Abstraction for local key-value storage operations.
 * 
 * This interface provides a clean abstraction layer between Koog's domain providers
 * (AgentMemoryProvider, PersistencyStorageProvider) and the underlying storage
 * implementation (Kottage, SQLite, etc.).
 * 
 * Key design principles:
 * - Implementation-agnostic: No exposure of backend-specific details
 * - Encryption-optional: Can be used with or without encryption layer
 * - Swappable backends: Easy to switch between storage implementations
 * - Clean separation: Domain logic separate from storage implementation
 * 
 * This abstraction enables:
 * - Standard storage via LocalKVStorage(backend)
 * - Encrypted storage via EncryptedLocalKVStorage(backend, keyProvider)
 * - Easy testing with mock implementations
 * - Future backend flexibility (Redis, cloud storage, etc.)
 * 
 * Users interact with LocalAgentMemoryProvider and LocalPersistencyProvider
 * which use this abstraction internally.
 */
public interface LocalKVStorage {
    /**
     * Retrieves a value by key.
     * 
     * This method:
     * 1. Fetches data from the underlying storage
     * 2. Applies any configured transformations (encryption/decryption)
     * 3. Returns the value
     * 
     * @param key The key to retrieve
     * @return Value or null if key doesn't exist
     * @throws SecurityException if decryption fails (encrypted implementations)
     * @throws StorageException if storage operation fails
     */
    public suspend fun get(key: String): String?

    /**
     * Stores a value by key.
     * 
     * This method:
     * 1. Applies any configured transformations (encryption) to the value
     * 2. Stores the data in the underlying storage
     * 3. Ensures atomic operation completion
     * 
     * @param key The key to store under
     * @param value The value to store
     * @throws SecurityException if encryption fails (encrypted implementations)
     * @throws StorageException if storage operation fails
     */
    public suspend fun put(key: String, value: String)

    /**
     * Deletes a key and its associated value.
     * 
     * This method:
     * 1. Removes the key-value pair from storage
     * 2. Ensures secure deletion (data is unrecoverable)
     * 3. No-op if key doesn't exist
     * 
     * @param key The key to delete
     * @throws StorageException if storage operation fails
     */
    public suspend fun delete(key: String)

    /**
     * Lists all keys matching a prefix pattern.
     * 
     * This method:
     * 1. Searches for keys matching the given prefix
     * 2. Returns keys only (values remain in storage)
     * 3. Useful for querying related data (e.g., all facts for a subject)
     * 
     * @param prefix Key prefix to match (e.g., "memory/agent/facts/")
     * @return List of matching keys
     * @throws StorageException if storage operation fails
     */
    public suspend fun keys(prefix: String): List<String>

    /**
     * Closes the storage connection and cleans up resources.
     * 
     * This method:
     * 1. Closes database connections
     * 2. Clears encryption keys from memory
     * 3. Releases any allocated resources
     * 
     * After calling close(), no further operations should be performed.
     */
    public suspend fun close()
}

/**
 * Exception thrown when storage operations fail.
 * 
 * This exception wraps underlying storage errors (database errors,
 * network issues, etc.) to provide a consistent error interface
 * for all storage implementations.
 */
public class StorageException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)