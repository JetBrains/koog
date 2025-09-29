@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.a2a.transport.jsonrpc.model

import ai.koog.a2a.model.RequestId
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Default JSON-RPC version.
 */
public const val JSONRPC_VERSION: String = "2.0"

@Serializable
public data class JSONRPCError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable(with = JSONRPCMessageSerializer::class)
public sealed interface JSONRPCMessage {
    public val jsonrpc: String
}

@Serializable(with = JSONRPCResponseSerializer::class)
public sealed interface JSONRPCResponse : JSONRPCMessage

@Serializable
public data class JSONRPCRequest(
    public val id: RequestId,
    val method: String,
    val params: JsonElement?,
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCMessage

@Serializable
public data class JSONRPCNotification(
    val method: String,
    val params: JsonElement?,
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCMessage

@Serializable
public data class JSONRPCSuccessResponse(
    public val id: RequestId,
    public val result: JsonElement,
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCResponse

@Serializable
public data class JSONRPCErrorResponse(
    public val id: RequestId?,
    public val error: JSONRPCError,
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCResponse
