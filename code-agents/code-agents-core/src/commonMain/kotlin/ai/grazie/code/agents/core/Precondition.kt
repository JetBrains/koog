package ai.grazie.code.agents.core

val Number.isPositive: Boolean get() = toDouble() > 0

/**
 * Throws an [IllegalArgumentException] if the [value] is less than or equal to `0`.
 */
inline fun <reified N : Number> requirePositive(value: N) {
    requirePositive(value) { "Required value was negative or zero." }
}

/**
 * Throws an [IllegalArgumentException] with the result of calling
 * [lazyMessage] if the [value] is less than or equal to `0`.
 */
inline fun <reified N : Number> requirePositive(value: N, lazyMessage: () -> Any) {
    if (!value.isPositive) {
        val message = lazyMessage()
        throw IllegalArgumentException(message.toString())
    }
}
