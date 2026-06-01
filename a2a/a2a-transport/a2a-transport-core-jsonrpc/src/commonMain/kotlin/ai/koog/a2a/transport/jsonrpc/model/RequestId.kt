package ai.koog.a2a.transport.jsonrpc.model

import ai.koog.a2a.transport.jsonrpc.serialization.RequestIdSerializer
import kotlinx.serialization.Serializable

/**
 * A uniquely identifying ID for a request.
 */
@Serializable(with = RequestIdSerializer::class)
public sealed interface RequestId {
    /**
     * A string representation of the ID.
     */
    @Serializable
    public data class StringId(val value: String) : RequestId

    /**
     * A numeric representation of the ID.
     */
    @Serializable
    public data class NumberId(val value: Long) : RequestId
}
