package ai.grazie.code.agents.core.event

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.ToolStage

/**
 * Handles various events and delegates actions based on internal handlers.
 * Handler methods are called on the same context as the calling agent,
 * see [ai.grazie.code.agents.core.AIAgent] **run** method.
 *
 * @property resultHandler Handler for processing successful results.
 * @property toolCallListener Listener to notify the tool invocation.
 * @property errorHandler Handler for processing errors
 */
class EventHandler private constructor(
    val initHandler: InitHandler,
    val resultHandler: ResultHandler,
    val errorHandler: ErrorHandler,
    val toolValidationFailureListener: ToolValidationFailureListener,
    val toolExceptionListener: ToolExceptionListener,
    val toolCallListener: ToolCallListener,
    val toolResultListener: ToolResultListener,
) {
    class Builder internal constructor(val nested: EventHandler? = null) {
        private var initHandler: InitHandler? = null
        private var resultHandler: ResultHandler? = null
        private var errorHandler: ErrorHandler? = null
        private var toolValidationFailureListener: ToolValidationFailureListener? = null
        private var toolExceptionListener: ToolExceptionListener? = null
        private var toolCallListener: ToolCallListener? = null
        private var toolResultListener: ToolResultListener? = null

        fun handleInit(handler: InitHandler) {
            require(initHandler == null) { "Init handler is already defined" }

            initHandler = handler
        }

        fun handleResult(handler: ResultHandler) {
            require(resultHandler == null) { "Result handler is already defined" }

            resultHandler = handler
        }

        fun handleError(handler: ErrorHandler) {
            require(errorHandler == null) { "Error handler is already defined" }

            errorHandler = handler
        }

        fun onInvalidToolCall(handler: ToolValidationFailureListener) {
            require(toolValidationFailureListener == null) { "Validation handler is already defined" }

            toolValidationFailureListener = handler
        }

        fun onToolFailed(handler: ToolExceptionListener) {
            require(toolExceptionListener == null) { "Validation handler is already defined" }

            toolExceptionListener = handler
        }

        fun onToolCall(listener: ToolCallListener) {
            require(toolCallListener == null) { "Tool call listener is already defined" }

            toolCallListener = listener
        }

        fun onToolResult(listener: ToolResultListener) {
            require(toolResultListener == null) { "Tool result listener is already defined" }

            toolResultListener = listener
        }

        internal fun build(): EventHandler = EventHandler(
            initHandler = {
                withNonNull(nested?.initHandler) {
                    this@EventHandler.handle()
                }
                withNonNull(initHandler) {
                    this@EventHandler.handle()
                }
            },
            resultHandler = { result ->
                withNonNull(nested?.resultHandler) {
                    this@EventHandler.handle(result)
                }
                withNonNull(resultHandler) {
                    this@EventHandler.handle(result)
                }
            },
            errorHandler = { result ->
                withNonNull(errorHandler) {
                    this@EventHandler.handle(result)
                } != false
                        ||
                        withNonNull(nested?.errorHandler) {
                            this@EventHandler.handle(result)
                        } != false
            },
            toolValidationFailureListener = { stage, tool, args, error ->
                withNonNull(nested?.toolValidationFailureListener) {
                    this@EventHandler.handle(stage, tool, args, error)
                }
                withNonNull(toolValidationFailureListener) {
                    this@EventHandler.handle(stage, tool, args, error)
                }
            },
            toolCallListener = { stage, tool, args ->
                nested?.toolCallListener?.call(stage, tool, args)
                toolCallListener?.call(stage, tool, args)
            },
            toolResultListener = { stage, tool, args, result ->
                nested?.toolResultListener?.result(stage, tool, args, result)
                toolResultListener?.result(stage, tool, args, result)
            },
            toolExceptionListener = { stage, tool, args, error ->
                withNonNull(nested?.toolExceptionListener) {
                    this@EventHandler.handle(stage, tool, args, error)
                }
                withNonNull(toolExceptionListener) {
                    this@EventHandler.handle(stage, tool, args, error)
                }
            },
        )
    }

    companion object {
        operator fun invoke(nested: EventHandler? = null, init: Builder.() -> Unit): EventHandler =
            Builder(nested).apply(init).build()

        val NO_HANDLER = EventHandler {}

        private inline fun <T, R> withNonNull(receiver: T?, block: T.() -> R?): R? {
            if (receiver == null) return null
            return with(receiver) {
                block()
            }
        }
    }
}

data class AgentHandlerContext(
    val strategyName: String
)

fun interface ErrorHandler {
    /**
     * @return `true` if an error was successfully handled.
     * `false` if not, in which case it will be rethrown.
     */
    suspend fun AgentHandlerContext.handle(thr: Throwable): Boolean
}

fun interface ToolValidationFailureListener {
    suspend fun AgentHandlerContext.handle(stage: ToolStage, tool: Tool<*, *>, args: Tool.Args, value: String)
}

fun interface ToolExceptionListener {
    suspend fun AgentHandlerContext.handle(stage: ToolStage, tool: Tool<*, *>, args: Tool.Args, exception: Exception)
}

fun interface InitHandler {
    suspend fun AgentHandlerContext.handle()
}

fun interface ResultHandler {
    suspend fun AgentHandlerContext.handle(value: String?)
}

fun interface ToolCallListener {
    suspend fun call(stage: ToolStage, tool: Tool<*, *>, args: Tool.Args)
}

fun interface ToolResultListener {
    suspend fun result(stage: ToolStage, tool: Tool<*, *>, args: Tool.Args, result: ToolResult?)
}