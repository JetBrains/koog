package ai.koog.a2a.validation

/**
 * Checks if the [actualVersion] has the same major version as the [requiredVersion].
 */
public fun isVersionCompatible(actualVersion: String, requiredVersion: String): Boolean {
    val actualMajor = actualVersion.substringBefore(".")
    val requiredMajor = requiredVersion.substringBefore(".")

    return actualMajor == requiredMajor
}
