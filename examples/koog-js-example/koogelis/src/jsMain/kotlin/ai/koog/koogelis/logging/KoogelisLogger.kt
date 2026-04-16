package ai.koog.koogelis.logging

@JsExport
actual external interface KoogelisLogger {
    actual public fun info(message: String)
    actual public fun debug(message: String)
    actual public fun trace(message: String)
    actual public fun warn(message: String)
    actual public fun error(message: String)
}