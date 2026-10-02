package ai.koog.a2a.validation

import ai.koog.a2a.consts.A2AVersions

/**
 * Normalizes an A2A protocol [version] to its `Major.Minor` form, ignoring patch and any further components.
 * A blank [version] is treated as [A2AVersions.VERSION_0_3], as required by the spec for an empty `A2A-Version` value.
 */
public fun normalizeVersion(version: String?): String {
    if (version.isNullOrBlank()) return A2AVersions.VERSION_0_3

    return version.trim().split(".").take(2).joinToString(".")
}

/**
 * Checks if the [actualVersion] has the same `Major.Minor` version as the [requiredVersion].
 * Patch versions are ignored. A blank version is treated as [A2AVersions.VERSION_0_3].
 */
public fun isVersionCompatible(actualVersion: String?, requiredVersion: String?): Boolean {
    return normalizeVersion(actualVersion) == normalizeVersion(requiredVersion)
}
