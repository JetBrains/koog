package ai.koog.protocol.tool

/**
 *
 */
public sealed interface FlowTool {

    /**
     *
     */
    public sealed interface Mcp : FlowTool {

        /**
         *
         */
        public data class Stdio(public val command: String, public val args: List<String> = emptyList()) : Mcp

        /**
         *
         */
        public data class SSE(public val url: String, public val headers: Map<String, String> = emptyMap()) : Mcp
    }

    /**
     *
     */
    public data class Local(public val fqn: String) : FlowTool
}
