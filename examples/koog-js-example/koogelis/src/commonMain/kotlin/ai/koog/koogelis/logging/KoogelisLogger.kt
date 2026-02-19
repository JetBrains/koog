package ai.koog.koogelis.logging

expect interface KoogelisLogger {
    public fun info(message: String)
    public fun debug(message: String)
    public fun trace(message: String)
    public fun warn(message: String)
    public fun error(message: String)
}