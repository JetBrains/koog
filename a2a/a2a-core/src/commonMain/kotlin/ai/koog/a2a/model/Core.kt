package ai.koog.a2a.model

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
 * Marker interface for request params data types.
 */
@Serializable
public sealed interface RequestData

/**
 * Marker interface for response data types.
 */
@Serializable
public sealed interface ResponseData

/**
 * Represents a request containing a unique identifier.
 *
 * @property id The unique identifier for the request.
 * @property data The data payload of the request.
 */
public class Request<T : RequestData>(
    public val id: RequestId,
    public val data: T,
)

/**
 * Represents a response associated with a request identifier.
 *
 * @property id The unique identifier for the request associated with this response.
 * @property data The response data payload.
 */
public class Response<T : ResponseData>(
    public val id: RequestId,
    public val data: T,
)
