package ai.koog.agents.secure.crypto

/**
 * Windows implementation of platform key provider factory.
 * Uses DPAPI and Windows Credential Manager for secure key storage.
 */
public actual fun createPlatformKeyProvider(
    keyIdentifier: String,
    serviceName: String,
    fallbackProvider: EncryptionKeyProvider?
): EncryptionKeyProvider {
    val windowsProvider = WindowsCredentialKeyProvider(keyIdentifier, serviceName)
    
    return if (windowsProvider.isAvailable()) {
        windowsProvider
    } else {
        fallbackProvider ?: throw PlatformSecurityException(
            "Windows Credential Manager is not available and no fallback provider specified"
        )
    }
}