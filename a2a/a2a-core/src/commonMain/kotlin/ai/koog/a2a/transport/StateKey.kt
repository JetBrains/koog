package ai.koog.a2a.transport

/**
 * Helper class to be used with [ServerCallContext.state] or [ClientCallContext.state] to store and retrieve values associated with a key in a typed
 * manner.
 *
 * @see ServerCallContext
 */
public data class StateKey<@Suppress("unused") T>(public val name: String) {
    override fun toString(): String = "${super.toString()}(name=$name)"
}
