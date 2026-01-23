package ai.koog.tools

import kotlinx.serialization.Serializable

/**
 * TODO: SD --
 */
@Serializable
public sealed interface FlowTool {

    /**
     *
     */
    public interface Mcp : FlowTool {

        /**
         *
         */
        @Serializable
        public data class StdIO(public val command: String, public val args: List<String> = emptyList()) : Mcp

        /**
         *
         */
        @Serializable
        public data class SSE(public val url: String, public val headers: Map<String, String> = emptyMap()) : Mcp
    }
}
