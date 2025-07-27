package ai.koog.agents.secure.crypto

/**
 * Abstraction for providing encryption keys to secure storage implementations.
 * 
 * This interface enables flexible key management strategies for different deployment scenarios:
 * - Development: Environment variables or simple passphrases
 * - Production: Secure passphrase derivation with salt/iterations
 * - Enterprise: Hardware security modules or cloud key management services
 * 
 * Security considerations:
 * - Keys should be at least 256 bits (32 bytes) for AES-256
 * - Key derivation should use appropriate algorithms (PBKDF2, Argon2, etc.)
 * - Keys should be securely wiped from memory after use
 * - Consider key rotation capabilities for long-term deployments
 * 
 * Usage example:
 * ```kotlin
 * val keyProvider = EnvVarKeyProvider("KOOG_ENCRYPTION_KEY")
 * val encoder = AESGCMKottageEncoder(keyProvider)
 * val storage = KottageAgentMemoryProvider(..., keyProvider, ...)
 * ```
 */
public interface EncryptionKeyProvider {
    /**
     * Retrieves the current encryption key for data protection operations.
     * 
     * This method should:
     * - Return a cryptographically strong key (minimum 256 bits for AES-256)
     * - Be idempotent - same key returned for same configuration
     * - Handle key derivation securely if using passphrases
     * - Protect keys in memory and clear sensitive data appropriately
     * 
     * @return 32-byte encryption key for AES-256-GCM operations
     * @throws SecurityException if key cannot be retrieved or is invalid
     */
    public suspend fun getEncryptionKey(): ByteArray

    /**
     * Validates that the encryption key meets security requirements.
     * 
     * Default implementation checks:
     * - Key length is exactly 32 bytes (256 bits)
     * - Key is not all zeros (weak key detection)
     * 
     * Implementations can override to add additional security checks:
     * - Entropy analysis
     * - Key strength validation
     * - Compliance with organizational security policies
     * 
     * @param key The encryption key to validate
     * @throws SecurityException if key fails validation requirements
     */
    public fun validateKey(key: ByteArray) {
        require(key.size == 32) {
            "Encryption key must be exactly 32 bytes (256 bits) for AES-256-GCM, got ${key.size} bytes"
        }
        
        require(!key.all { it == 0.toByte() }) {
            "Encryption key cannot be all zeros - this indicates a weak or uninitialized key"
        }
    }

    /**
     * Future extension point for key rotation capabilities.
     * 
     * This method would enable:
     * - Periodic key rotation for enhanced security
     * - Re-encryption of existing data with new keys  
     * - Key versioning and migration strategies
     * 
     * Current implementation throws UnsupportedOperationException.
     * Future implementations should:
     * - Generate a new cryptographically strong key
     * - Handle migration of existing encrypted data
     * - Update key storage/retrieval mechanisms
     * 
     * @return New encryption key after rotation
     * @throws UnsupportedOperationException if key rotation is not supported
     */
    public suspend fun rotateKey(): ByteArray {
        throw UnsupportedOperationException("Key rotation not yet implemented")
    }
}