package ai.koog.a2a.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable


/**
 * Representation of the Google's `ErrorInfo` gRPC object accordign to A2A protocol.
 */
@Serializable
public data class ErrorInfo(
    public val reason: String,
    public val metadata: Map<String, String>? = null,
) {
    @EncodeDefault
    public val type: String = "type.googleapis.com/google.rpc.ErrorInfo"

    @EncodeDefault
    public val domain: String = "a2a-protocol.org"
}
