package ai.koog.agents.secure.crypto

/**
 * Platform-native key provider that leverages OS-level secure storage.
 * 
 * This interface enables platform-specific implementations that use:
 * - macOS/iOS: Security framework and Keychain Services
 * - Windows: DPAPI and Windows Credential Manager  
 * - Linux: libsecret, gnome-keyring, or kwallet
 * 
 * Platform-native providers offer superior security compared to passphrase-based providers:
 * - Keys are protected by OS-level authentication (Touch ID, Windows Hello, etc.)
 * - Hardware-backed security on supported platforms (Secure Enclave, TPM)
 * - No user-managed passphrases that can be forgotten or compromised
 * - Automatic key derivation and storage handled by OS security frameworks
 */
public interface PlatformKeyProvider : EncryptionKeyProvider {
    
    /**
     * Unique identifier for this key in the platform's secure storage.
     * Used to distinguish between different applications/contexts.
     */
    public val keyIdentifier: String
    
    /**
     * Service name for the key, used for grouping related keys.
     * Typically your application's bundle identifier or service name.
     */
    public val serviceName: String
    
    /**
     * Whether this platform provider is available on the current system.
     * Returns false if the required platform security frameworks are not available.
     */
    public fun isAvailable(): Boolean
    
    /**
     * Stores the encryption key in platform-native secure storage.
     * May prompt for user authentication (Touch ID, password, etc.)
     * 
     * @param key The encryption key to store securely
     * @throws PlatformSecurityException if the platform security system is unavailable
     * @throws AuthenticationRequiredException if user authentication fails
     */
    public suspend fun storeKey(key: ByteArray)
    
    /**
     * Removes the encryption key from platform-native secure storage.
     * May prompt for user authentication depending on platform policy.
     * 
     * @throws PlatformSecurityException if the platform security system is unavailable
     * @throws AuthenticationRequiredException if user authentication fails
     */
    public suspend fun removeKey()
    
    /**
     * Checks if a key exists in platform-native secure storage.
     * This operation typically doesn't require authentication.
     */
    public suspend fun hasKey(): Boolean
}

/**
 * Exception thrown when platform-native security systems are unavailable or fail.
 */
public class PlatformSecurityException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when user authentication is required but fails.
 */
public class AuthenticationRequiredException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Creates a platform-appropriate key provider for the current operating system.
 * 
 * @param keyIdentifier Unique identifier for this key
 * @param serviceName Service name (typically your app's bundle ID)
 * @param fallbackProvider Provider to use if platform-native security is unavailable
 * @return Platform-specific key provider, or fallback if platform security unavailable
 */
public expect fun createPlatformKeyProvider(
    keyIdentifier: String,
    serviceName: String,
    fallbackProvider: EncryptionKeyProvider? = null
): EncryptionKeyProvider