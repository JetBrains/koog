package ai.koog.a2a.consts

/**
 * A2A protocol versions
 */
public object A2AVersions {
    /**
     * Protocol version 0.3. Per the spec, it is assumed when the `A2A-Version` header is empty.
     */
    public const val VERSION_0_3: String = "0.3"

    public const val VERSION_1_0: String = "1.0"

    /**
     * Latest supported protocol version, returns one of the versions listed in the [A2AVersions]
     */
    public val CURRENT_VERSION: String get() = VERSION_1_0
}
