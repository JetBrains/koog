@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.a2a.model

import kotlinx.serialization.Serializable

/**
 * A uniquely identifying ID for a request.
 */
@Serializable(with = RequestIdSerializer::class)
public sealed interface RequestId {
    @Serializable
    public data class StringId(val value: String) : RequestId

    @Serializable
    public data class NumberId(val value: Long) : RequestId
}
