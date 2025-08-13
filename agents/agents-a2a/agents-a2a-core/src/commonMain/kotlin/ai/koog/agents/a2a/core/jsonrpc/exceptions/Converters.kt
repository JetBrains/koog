@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.a2a.core.jsonrpc.exceptions

import ai.koog.agents.a2a.core.jsonrpc.model.JSONRPCError

public fun JSONRPCError.toJSONRPCException(): JSONRPCException = when (code) {
    JSONRPCErrorCode.ParseError.code -> ParseErrorJSONRPCException(message)
    JSONRPCErrorCode.InvalidRequest.code -> InvalidRequestJSONRPCException(message)
    JSONRPCErrorCode.MethodNotFound.code -> MethodNotFoundJSONRPCException(message)
    JSONRPCErrorCode.InvalidParams.code -> InvalidParamsJSONRPCException(message)
    JSONRPCErrorCode.InternalError.code -> InternalErrorJSONRPCException(message)
    else -> JSONRPCException(code, message)
}

public fun JSONRPCException.toJSONRPCError(): JSONRPCError = JSONRPCError(code, message)
