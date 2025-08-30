package ai.koog.a2a.transport

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

/**
 * Represents a request containing a unique identifier.
 *
 * @property id The unique identifier for the request.
 * @property data The data payload of the request.
 */
public class Request<T>(
    public val id: RequestId,
    public val data: T,
)

/**
 * Represents a response associated with a request identifier.
 *
 * @property id The unique identifier for the request associated with this response.
 * @property data The response data payload.
 */
public class Response<T>(
    public val id: RequestId,
    public val data: T,
)
