package ai.grazie.agents.mcp

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered


fun main() {
    val apiKey = System.getenv("GOOGLE_MAPS_API_KEY")

    val process = ProcessBuilder(
        "docker", "run", "-i",
        "-e", "GOOGLE_MAPS_API_KEY=$apiKey",
        "mcp/google-maps"
    ).start()

    Thread.sleep(2000)

    val mcp = Client(clientInfo = Implementation(name = "mcp-client-cli", version = "1.0.0"))
    // Setup I/O transport using the process streams
    val transport = StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered()
    )

    runBlocking {
        // Connect the MCP client to the server using the transport
        mcp.connect(transport)

        val toolsResult = mcp.listTools()
        val tools = toolsResult?.tools?.map { tool ->
            println(tool.name)
            println(tool.description)
            println(tool.inputSchema)
            tool
        } ?: emptyList()

        println(tools)

        val toolCallResult = mcp.callTool(
            name = "maps_geocode",
            arguments = mapOf("address" to "1600 Amphitheatre Parkway, Mountain View, CA")
        )

        println(toolCallResult?.content)
    }
}
