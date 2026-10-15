package ai.koog.a2a.validation

import ai.koog.a2a.consts.A2AVersions
import ai.koog.a2a.exceptions.A2AVersionNotSupportedException

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

/**
 * Checks that the [requestedVersion] is one of the [supportedVersions], comparing only `Major.Minor`, see
 * [isVersionCompatible]. A null or blank [requestedVersion] is treated as [A2AVersions.VERSION_0_3], as the spec
 * requires for an empty `A2A-Version` value. Transport-independent, so any server transport can reuse it.
 *
 * @param requestedVersion The version requested by the client, e.g. the value of the `A2A-Version` header.
 * @param supportedVersions The versions supported by the server.
 * @throws A2AVersionNotSupportedException if the requested version is not supported.
 */
public fun requireSupportedVersion(requestedVersion: String?, supportedVersions: Set<String>) {
    if (supportedVersions.none { isVersionCompatible(requestedVersion, it) }) {
        throw A2AVersionNotSupportedException(
            "A2A version '${normalizeVersion(requestedVersion)}' is not supported; " +
                "supported: ${supportedVersions.joinToString(", ")}"
        )
    }
}
