# Google Maps MCP with Koog: From Zero to Elevation in a Kotlin Notebook

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/GoogleMapsMcp.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/GoogleMapsMcp.ipynb
){ .md-button }

In this short, blog-style walkthrough, we’ll connect Koog to a Model Context Protocol (MCP) server for Google Maps. We’ll spin up the server with Docker, discover the available tools, and let an AI agent geocode an address and fetch its elevation — all from a Kotlin Notebook.

By the end, you’ll have a reproducible, end‑to‑end example you can drop into your workflow or documentation.



```kotlin
%useLatestDescriptors
%use koog

```

## Prerequisites
Before you run the cells below, make sure you have:

- Docker installed and running
- A valid Google Maps API key exported as an environment variable: `GOOGLE_MAPS_API_KEY`
- An OpenAI API key exported as `OPENAI_API_KEY`

You can set them in your shell like this (macOS/Linux example):

```bash
export GOOGLE_MAPS_API_KEY="<your-key>"
export OPENAI_API_KEY="<your-openai-key>"
```



```kotlin
// Get the API key from environment variables
val googleMapsApiKey = System.getenv("GOOGLE_MAPS_API_KEY") ?: error("GOOGLE_MAPS_API_KEY environment variable not set")
val openAIApiToken = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable not set")

```

## Start the Google Maps MCP server (Docker)
We’ll use the official `mcp/google-maps` image. The container will expose tools such as `maps_geocode` and `maps_elevation` over MCP. We pass the API key via environment variables and launch it attached so the notebook can talk to it over stdio.



```kotlin
// Start the Docker container with the Google Maps MCP server
val process = ProcessBuilder(
    "docker",
    "run",
    "-i",
    "-e",
    "GOOGLE_MAPS_API_KEY=$googleMapsApiKey",
    "mcp/google-maps"
).start()

```

## Discover tools via McpToolRegistry
Koog can connect to an MCP server over stdio. Here, we create a tool registry from the running process and print out the discovered tools and their descriptors.



```kotlin
val toolRegistry = McpToolRegistryProvider.fromTransport(
    transport = McpToolRegistryProvider.defaultStdioTransport(process)
)
toolRegistry.tools.forEach {
    println(it.name)
    println(it.descriptor)
}

```

## Build an AI Agent with OpenAI
Next we assemble a simple agent backed by the OpenAI executor and model. The agent will be able to call tools exposed by the MCP server through the registry we just created.



```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(openAIApiToken),
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = toolRegistry,
)

```

## Ask for elevation: geocode first, then elevation
We prompt the agent to find the elevation of the JetBrains office in Munich. The instruction explicitly tells the agent to use only the available tools and which ones to prefer for the task.



```kotlin
import kotlinx.coroutines.runBlocking

val request = "Get elevation of the Jetbrains Office in Munich, Germany?"
runBlocking {
    agent.run(
        request +
            "You can only call tools. Get it by calling maps_geocode and maps_elevation tools."
    )
}

```

## Clean up
When you’re done, stop the Docker process so you don’t leave anything running in the background.



```kotlin
process.destroy()

```

## Troubleshooting and next steps
- If the container fails to start, check that Docker is running and your `GOOGLE_MAPS_API_KEY` is valid.
- If the agent can’t call tools, re-run the discovery cell to ensure the tool registry is populated.
- Try other prompts like route planning or place searches using the available Google Maps tools.

Next, consider composing multiple MCP servers (e.g., Playwright for web automation + Google Maps) and let Koog orchestrate tool usage for richer tasks.
