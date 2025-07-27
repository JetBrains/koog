package ai.koog.agents.secure.crypto

import kotlinx.serialization.Serializable

/**
 * Simple encryption key provider that uses a hardcoded key for testing.
 * 
 * ⚠️  **SECURITY WARNING**: This implementation is for testing only!
 * Do not use in production environments.
 * 
 * For production use, consider:
 * - PassphraseKeyProvider for password-based key derivation
 * - External key management systems
 * - Secure environment variable injection
 * 
 * Usage example:
 * ```kotlin
 * val keyProvider = SimpleKeyProvider("test-key-for-development-only")
 * val storage = EncryptedKVStorage(backend, keyProvider)
 * ```
 */
@Serializable
public data class SimpleKeyProvider(
    /**
     * A simple identifier for the key (not the actual key material).
     * The actual key is derived from this identifier.
     */
    private val keyIdentifier: String
) : EncryptionKeyProvider {

    /**
     * Generates a deterministic 32-byte key from the identifier.
     * 
     * ⚠️  **SECURITY WARNING**: This is not cryptographically secure!
     * Use only for testing and development.
     * 
     * @return 32-byte key derived from the identifier
     */
    override suspend fun getEncryptionKey(): ByteArray {
        // Simple key derivation for testing (NOT secure for production)
        val keyBytes = keyIdentifier.encodeToByteArray()
        val result = ByteArray(32)
        
        // Fill the 32-byte array with a pattern based on the identifier
        for (i in 0 until 32) {
            result[i] = ((keyBytes[i % keyBytes.size].toInt() + i) and 0xFF).toByte()
        }
        
        validateKey(result)
        return result
    }

    override fun toString(): String {
        return "SimpleKeyProvider(keyIdentifier='${keyIdentifier.take(8)}...')"
    }
}