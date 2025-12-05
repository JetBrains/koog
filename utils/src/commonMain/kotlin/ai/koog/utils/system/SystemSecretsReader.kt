package ai.koog.utils.system

/**
 * An interface for reading secrets.
 *
 * This interface provides a method to retrieve secret values by their names.
 * Implementations can vary based on the operating system or platform being used.
 */
public interface SystemSecretsReader {

    /**
     * Retrieves a secret value from environment variables.
     *
     * **SECURITY NOTICE**:
     * Secrets are returned as Strings because the underlying OS APIs
     * (environment variables, system properties) return Strings.
     * The String cannot be cleared from memory.
     *
     * **Best practices**:
     * - Use platform-specific secure storage when possible (Keychain, KeyStore)
     * - Use short-lived tokens instead of long-lived secrets
     * - Rotate secrets regularly
     * - Don't log or print secret values
     *
     * For maximum security, consider using:
     * - Secrets management services (HashiCorp Vault, AWS Secrets Manager)
     * - Platform secure storage APIs
     * - Memory-mapped files with OS-level protection
     *
     * @param name The secret key
     * @return The secret value, or null if not found
     */
    public fun getSecret(name: String): String?
}

/**
 * Provides an implementation of the [SystemSecretsReader] interface for the current platform.
 *
 * This function returns an instance of [SystemSecretsReader] that allows access
 * to secrets based on platform-specific implementations.
 * On unsupported platforms, this function will throw a [NotImplementedError].
 *
 * @return An instance of [SystemSecretsReader] for accessing system configuration properties.
 * @throws NotImplementedError if [SystemSecretsReader] is not supported on this platform.
 */
public expect fun systemSecretsReader(): SystemSecretsReader
