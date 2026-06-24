package ai.koog.a2a.consts

/**
 * A2A protocol versions
 */
public object A2AVersions {
    public const val VERSION_1_0: String = "1.0"

    /**
     * Latest supported protocol version, returns one of the versions listed in the [A2AVersions]
     */
    public val CURRENT_VERSION: String get() = VERSION_1_0
}
