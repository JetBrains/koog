package ai.koog.utils.system

/**
 * Reads system configuration properties in a JVM environment.
 *
 * JvmSystemConfigReader is responsible for retrieving the values of system configuration
 * properties using both environment variables and system properties.
 *
 * The property name provided is automatically converted to a format compatible with
 * system environment variable conventions by transforming the name to lowercase and
 * replacing underscores with dots. It attempts to fetch the value from both the original
 * name and the transformed format.
 *
 * Implements:
 * - SystemConfigReader: Provides the ability to retrieve configuration properties.
 */
public object JvmSystemConfigReader : SystemConfigReader {

    /**
     * Retrieves the value of the specified environment variable or system property.
     *
     * The method first attempts to retrieve the value from the environment variables.
     * If it is not found, it then attempts to retrieve the value from the system properties.
     * When checking system properties, the name is converted to lowercase and underscores
     * are replaced with dots.
     *
     * @param name the name of the environment variable or system property to retrieve
     * @return the value of the environment variable or system property, or null if not found
     */
    override fun getConfigVariable(name: String): String? {
        return System.getenv(name)
            ?: System.getProperty(name)
            ?: System.getProperty(normalizePropertyName(name))
    }

    /**
     * Normalizes a property name by converting it to lowercase and replacing underscores with dots.
     *
     * This transformation ensures compatibility with system property naming conventions
     * and is typically used to access properties that might exist in different formats.
     *
     * @param name the original property name to normalize
     * @return the normalized property name
     */
    private fun normalizePropertyName(name: String): String {
        return name.lowercase().replace('_', '.')
    }
}
