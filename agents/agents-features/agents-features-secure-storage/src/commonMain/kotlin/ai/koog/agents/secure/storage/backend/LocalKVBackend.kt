package ai.koog.agents.secure.storage.backend

/**
 * Internal abstraction for swappable key-value storage backends.
 * 
 * This interface defines the low-level storage operations that different
 * backend implementations must provide. Backends handle raw storage without
 * any awareness of encryption or domain-specific logic.
 * 
 * Key characteristics:
 * - Raw storage operations only (no encryption/decryption)
 * - Backend-specific optimizations handled internally
 * - Swappable implementations (Kottage, SQLite, Redis, etc.)
 * - Platform-specific capabilities (file system, network, etc.)
 * 
 * This separation enables:
 * - Clean testing with mock backends
 * - Easy migration between storage technologies
 * - Platform-specific optimizations without API changes
 * - Consistent behavior across all backends
 * 
 * Note: This interface is internal and should never be exposed in public APIs.
 * Users interact with LocalKVStorage implementations that wrap these backends.
 */
public interface LocalKVBackend {
    /**
     * Retrieves raw data by key from the backend storage.
     * 
     * This method:
     * 1. Performs backend-specific key lookup
     * 2. Returns raw data without any transformations
     * 3. Handles backend-specific error conditions
     * 
     * @param key The key to retrieve
     * @return Raw data or null if key doesn't exist
     * @throws StorageException if backend operation fails
     */
    public suspend fun get(key: String): String?

    /**
     * Stores raw data by key in the backend storage.
     * 
     * This method:
     * 1. Stores data using backend-specific mechanisms
     * 2. Ensures atomic operation completion
     * 3. Handles backend-specific optimizations (batching, indexing, etc.)
     * 
     * @param key The key to store under
     * @param value The raw data to store
     * @throws StorageException if backend operation fails
     */
    public suspend fun put(key: String, value: String)

    /**
     * Deletes a key and its associated data from backend storage.
     * 
     * This method:
     * 1. Removes key-value pair using backend-specific deletion
     * 2. Ensures data is properly cleaned up
     * 3. No-op if key doesn't exist
     * 
     * @param key The key to delete
     * @throws StorageException if backend operation fails
     */
    public suspend fun delete(key: String)

    /**
     * Lists all keys matching a prefix pattern in backend storage.
     * 
     * This method:
     * 1. Performs backend-specific key enumeration
     * 2. Filters keys by prefix using backend capabilities
     * 3. Returns keys only (no value retrieval)
     * 
     * @param prefix Key prefix to match
     * @return List of matching keys
     * @throws StorageException if backend operation fails
     */
    public suspend fun keys(prefix: String): List<String>

    /**
     * Closes the backend connection and releases resources.
     * 
     * This method:
     * 1. Closes backend-specific connections (database, file handles, etc.)
     * 2. Releases allocated resources
     * 3. Ensures proper cleanup for the specific backend type
     * 
     * After calling close(), no further operations should be performed.
     */
    public suspend fun close()
}