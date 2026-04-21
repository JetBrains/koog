package ai.koog.utils.system

/**
 * Reads secrets from environment variables in a JVM environment.
 *
 * EnvSystemSecretsReader retrieves secret values from environment variables only.
 * It does not check system properties for security reasons, as system properties
 * are JVM-wide and more easily accessible than environment variables.
 *
 * Implements:
 * - SystemSecretsReader: Provides the ability to retrieve secrets.
 */
public object EnvSystemSecretsReader : SystemSecretsReader {

    /**
     * Retrieves a secret value by its name from the specified environment variable.
     *
     * @param name the name of the environment variable to retrieve.
     * @return the value of the environment variable as a String, or null if the variable is not found.
     */
    override fun getSecret(name: String): String? = System.getenv(name)
}
