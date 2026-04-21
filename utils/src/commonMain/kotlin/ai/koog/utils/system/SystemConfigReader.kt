package ai.koog.utils.system

/**
 * An interface for reading system configuration properties.
 *
 * This interface provides a method to retrieve system configuration values by their names.
 * Implementations can vary based on the operating system or platform being used.
 */
public interface SystemConfigReader {

    /**
     * Retrieves the value of a system configuration property by its name.
     *
     * @param name The name of the system configuration property to retrieve.
     * @return The value of the configuration property if it exists, or null if not found.
     */
    public fun getConfigVariable(name: String): String?
}

/**
 * Provides an implementation of the [SystemConfigReader] interface for the current platform.
 *
 * This function returns an instance of [SystemConfigReader] that allows access
 * to system-level configuration properties based on platform-specific implementations.
 * On unsupported platforms, this function will throw a [NotImplementedError].
 *
 * @return An instance of [SystemConfigReader] for accessing system configuration properties.
 * @throws NotImplementedError if [SystemConfigReader] is not supported on this platform.
 */
public expect fun systemConfigReader(): SystemConfigReader
