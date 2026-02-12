package ai.koog.protocol.flow

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM implementation of the stdio process holder for tracking and cleanup.
 */
internal actual class StdioProcessHolder {
    private val processes = ConcurrentHashMap<String, Process>()

    actual fun addProcess(command: String, args: List<String>, process: Any) {
        if (process is Process) {
            val key = "$command ${args.joinToString(" ")}"
            processes[key] = process
        }
    }

    actual fun cleanup() {
        processes.values.forEach { process ->
            try {
                if (process.isAlive) {
                    // Try graceful termination first
                    process.destroy()
                    // Wait briefly for graceful termination
                    process.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)

                    // Force kill if still alive
                    if (process.isAlive) {
                        process.destroyForcibly()
                        // Wait for forceful termination
                        process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        processes.clear()
    }
}

/**
 * JVM-specific implementation for creating a ToolRegistry from a stdio MCP tool.
 *
 * This function launches an external process with the specified command and arguments,
 * creates a stdio transport for MCP communication, and builds a ToolRegistry containing
 * all tools from the MCP server.
 *
 * @param command The executable command to run (e.g., "npx", "python", "./my-tool")
 * @param args List of command-line arguments to pass to the command
 * @param processHolder Holder to track the launched process for cleanup
 * @return A ToolRegistry containing all tools from the MCP server, or EMPTY if the process fails to start
 */
internal actual suspend fun buildStdioToolRegistry(
    command: String,
    args: List<String>,
    processHolder: StdioProcessHolder
): ToolRegistry {
    return try {
        // Launch the external process
        val processBuilder = ProcessBuilder(listOf(command) + args)
        val process = processBuilder.start()

        // Track the process for cleanup
        processHolder.addProcess(command, args, process)

        // Create the stdio transport from the process
        val transport = McpToolRegistryProvider.defaultStdioTransport(process)

        // Build the tool registry from the transport
        McpToolRegistryProvider.fromTransport(transport)
    } catch (e: Exception) {
        // If process fails to start or connection fails, return empty registry
        // The error will be logged by the caller
        ToolRegistry.EMPTY
    }
}
