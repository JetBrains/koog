package ai.koog.protocol.tool

/**
 * Runtime representation of tools available to agents in a flow.
 *
 * Tools extend agent capabilities beyond LLM inference, allowing them to perform
 * actions like API calls, file operations, database queries, or external computations.
 */
public sealed interface FlowTool {

    /**
     * Model Context Protocol (MCP) tool integrations for external service communication.
     *
     * MCP provides a standardized way to connect agents to external tools and services.
     */
    public sealed interface Mcp : FlowTool {

        /**
         * Standard input/output (stdio) transport for MCP tools.
         *
         * Launches an external process and communicates via standard input/output streams.
         * Suitable for local command-line tools and scripts.
         *
         * @property command The executable command to run (e.g., "python", "node", "./my-tool")
         * @property args List of command-line arguments to pass to the command
         */
        public data class Stdio(public val command: String, public val args: List<String> = emptyList()) : Mcp

        /**
         * Server-Sent Events (SSE) transport for MCP tools.
         *
         * Connects to a remote MCP server via HTTP Server-Sent Events.
         * Suitable for network-based tools and cloud services.
         *
         * @property url The HTTP(S) endpoint URL for the SSE connection
         * @property headers Optional HTTP headers to include in the connection request (e.g., authentication)
         */
        public data class SSE(public val url: String, public val headers: Map<String, String> = emptyMap()) : Mcp
    }

    /**
     * Local tool implementation registered within the application.
     *
     * References a tool class that has been registered in the application's tool registry.
     * The tool must be available at runtime via the fully qualified name.
     *
     * @property fqn Fully qualified name or path identifier for the local tool class
     */
    public data class Local(public val fqn: String) : FlowTool
}
