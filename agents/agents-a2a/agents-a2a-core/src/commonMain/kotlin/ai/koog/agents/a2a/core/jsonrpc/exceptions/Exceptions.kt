@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.a2a.core.jsonrpc.exceptions

public enum class JSONRPCErrorCode(public val code: Int) {
    ParseError(-32700),
    InvalidRequest(-32600),
    MethodNotFound(-32601),
    InvalidParams(-32602),
    InternalError(-32603),
}

public open class JSONRPCException(
    public val code: Int,
    public override val message: String
) : Exception(message)

public class ParseErrorJSONRPCException(message: String) : JSONRPCException(JSONRPCErrorCode.ParseError.code, message)

public class InvalidRequestJSONRPCException(message: String) : JSONRPCException(JSONRPCErrorCode.InvalidRequest.code, message)

public class MethodNotFoundJSONRPCException(message: String) : JSONRPCException(JSONRPCErrorCode.MethodNotFound.code, message)

public class InvalidParamsJSONRPCException(message: String) : JSONRPCException(JSONRPCErrorCode.InvalidParams.code, message)

public class InternalErrorJSONRPCException(message: String) : JSONRPCException(JSONRPCErrorCode.InternalError.code, message)
