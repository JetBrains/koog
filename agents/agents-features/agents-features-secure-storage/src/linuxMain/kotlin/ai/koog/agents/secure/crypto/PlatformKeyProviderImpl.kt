package ai.koog.agents.secure.crypto

/**
 * Linux implementation of platform key provider factory.
 * Uses libsecret/gnome-keyring or filesystem fallback for secure key storage.
 */
public actual fun createPlatformKeyProvider(
    keyIdentifier: String,
    serviceName: String,
    fallbackProvider: EncryptionKeyProvider?
): EncryptionKeyProvider {
    val linuxProvider = LinuxKeyringKeyProvider(keyIdentifier, serviceName)
    
    return if (linuxProvider.isAvailable()) {
        linuxProvider
    } else {
        fallbackProvider ?: throw PlatformSecurityException(
            "Linux keyring services are not available and no fallback provider specified"
        )
    }
}