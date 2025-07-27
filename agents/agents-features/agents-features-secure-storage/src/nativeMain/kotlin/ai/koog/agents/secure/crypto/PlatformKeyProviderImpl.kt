package ai.koog.agents.secure.crypto

/**
 * macOS/iOS implementation of platform key provider factory.
 * Uses Security framework and Keychain Services for secure key storage.
 */
public actual fun createPlatformKeyProvider(
    keyIdentifier: String,
    serviceName: String,
    fallbackProvider: EncryptionKeyProvider?
): EncryptionKeyProvider {
    val macOSProvider = MacOSKeychainKeyProvider(keyIdentifier, serviceName)
    
    return if (macOSProvider.isAvailable()) {
        macOSProvider
    } else {
        fallbackProvider ?: throw PlatformSecurityException(
            "macOS Keychain is not available and no fallback provider specified"
        )
    }
}