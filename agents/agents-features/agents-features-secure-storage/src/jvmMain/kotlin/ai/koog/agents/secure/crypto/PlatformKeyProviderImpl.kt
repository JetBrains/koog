package ai.koog.agents.secure.crypto

/**
 * JVM implementation of platform key provider factory.
 * 
 * On JVM, we detect the underlying platform and delegate to appropriate
 * platform-specific mechanisms when available, or fall back to the provided fallback.
 * 
 * This approach allows JVM applications to benefit from platform-native security
 * when running on supported platforms.
 */
public actual fun createPlatformKeyProvider(
    keyIdentifier: String,
    serviceName: String,
    fallbackProvider: EncryptionKeyProvider?
): EncryptionKeyProvider {
    val osName = System.getProperty("os.name").lowercase()
    
    // For JVM, we currently don't have direct platform integrations implemented
    // In a future enhancement, we could use JNI to call native platform APIs
    // or integrate with libraries like java-keyring for cross-platform keyring access
    
    return fallbackProvider ?: throw PlatformSecurityException(
        "Platform-native key storage is not yet implemented for JVM on $osName. " +
        "Please provide a fallback provider (e.g., PassphraseKeyProvider) for JVM applications."
    )
}