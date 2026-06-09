package ai.koog.a2a.exceptions

import ai.koog.a2a.serialization.ErrorDataSerializer
import ai.koog.a2a.serialization.GenericErrorDataSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Error data entry identified by a `@type` key.
 * It is used in the data array in error reponses, e.g.
 * [ai.koog.a2a.transport.jsonrpc.model.JSONRPCError], as per A2A spec.
 *
 * https://a2a-protocol.org/v1.0.1/specification/#332-error-handling
 */
@Serializable(with = ErrorDataSerializer::class)
public sealed interface ErrorData {
    /**
     * The `@type` discriminator for error data entry.
     */
    @SerialName(TYPE_KEY)
    public val type: String

    public companion object {
        public const val TYPE_KEY: String = "@type"
    }
}

/**
 * Representation of the Google's `ErrorInfo` gRPC object, according to A2A protocol.
 * https://github.com/googleapis/googleapis/blob/591ae025072c1608bd7b38039feeb296f640605a/google/rpc/error_details.proto#L51
 */
@Serializable
public data class ErrorInfo(
    public val reason: String,
    public val metadata: Map<String, String>? = null,
) : ErrorData {
    @EncodeDefault
    @SerialName(ErrorData.TYPE_KEY)
    override val type: String = TYPE

    @EncodeDefault
    public val domain: String = "a2a-protocol.org"

    public companion object {
        public const val TYPE: String = "type.googleapis.com/google.rpc.ErrorInfo"
    }
}

/**
 * Representation of the Google's `BadRequest` gRPC ojbect, according to A2A protocol.
 * https://github.com/googleapis/googleapis/blob/591ae025072c1608bd7b38039feeb296f640605a/google/rpc/error_details.proto#L236
 */
@Serializable
public data class BadRequest(
    public val fieldViolations: List<FieldViolation>,
) : ErrorData {
    @EncodeDefault
    @SerialName(ErrorData.TYPE_KEY)
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "type.googleapis.com/google.rpc.BadRequest"
    }

    @Serializable
    public data class FieldViolation(
        public val field: String,
        public val description: String,
        public val reason: String? = null,
    )
}

/**
 * Generic error data entry for all other unknown and custom error types
 *
 * @property raw The original raw JSON object representing error data.
 */
@Serializable(with = GenericErrorDataSerializer::class)
public data class GenericErrorData(
    public val raw: JsonObject,
    override val type: String,
) : ErrorData
