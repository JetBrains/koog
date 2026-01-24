package ai.koog.utils.system

import platform.Foundation.NSUserDefaults.Companion.standardUserDefaults

/**
 * A configuration reader implementation backed by Apple's `NSUserDefaults`.
 *
 * This class provides a mechanism to retrieve system configuration properties
 * stored in the `NSUserDefaults` system on Apple platforms.
 *
 * @constructor Initializes an instance of `UserDefaultsSystemConfigReader` with the provided `NSUserDefaults`.
 * @param userDefaults The `NSUserDefaults` instance used to fetch configuration values.
 */
public class UserDefaultsSystemConfigReader(
    private val userDefaults: platform.Foundation.NSUserDefaults
) : SystemConfigReader {

    public companion object {
        /**
         * A shared instance of [UserDefaultsSystemConfigReader].
         *
         * This instance provides centralized access to system configuration properties
         * stored in Apple's `NSUserDefaults`. It uses the standard `NSUserDefaults` instance
         * for fetching configuration values.
         *
         * The `shared` instance is intended for reuse across the application whenever access
         * to system configuration properties is required.
         */
        public val shared: UserDefaultsSystemConfigReader = UserDefaultsSystemConfigReader(standardUserDefaults())
    }

    /**
     * Retrieves the value of a system configuration property by its name.
     *
     * This method interacts with Apple's `NSUserDefaults` system to fetch
     * a configuration value based on the provided key.
     *
     * @param name The name of the configuration property to retrieve.
     * @return The value of the configuration property if it exists, or null if not found.
     */
    override fun getConfigVariable(name: String): String? {
        return userDefaults.stringForKey(name)
    }
}
