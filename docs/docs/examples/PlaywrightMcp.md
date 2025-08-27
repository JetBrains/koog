# Drive the browser with Playwright MCP and Koog

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/PlaywrightMcp.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/PlaywrightMcp.ipynb
){ .md-button }

In this notebook, you'll connect a Koog agent to Playwright's Model Context Protocol (MCP) server and let it drive a real browser to complete a task: open jetbrains.com, accept cookies, and click the AI section in the toolbar.

We'll keep things simple and reproducible, focusing on a minimal but realistic agent + tools setup you can publish and reuse.



```kotlin
%useLatestDescriptors
%use koog

```

## Prerequisites
- An OpenAI API key exported as an environment variable: `OPENAI_API_KEY`
- Node.js and npx available on your PATH
- Kotlin Jupyter notebook environment with Koog available via `%use koog`

Tip: Run the Playwright MCP server in headful mode to watch the browser automate the steps.


## 1) Provide your OpenAI API key
We read the API key from the `OPENAI_API_KEY` environment variable. This keeps secrets out of the notebook.



```kotlin
// Get the API key from environment variables
val openAIApiToken = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable not set")

```

## 2) Start the Playwright MCP server
We'll launch Playwright's MCP server locally using `npx`. By default, it will expose an SSE endpoint we can connect to from Koog.



```kotlin
// Start the Playwright MCP server via npx
val process = ProcessBuilder(
    "npx",
    "@playwright/mcp@latest",
    "--port",
    "8931"
).start()

```

## 3) Connect from Koog and run the agent
We build a minimal Koog `AIAgent` with an OpenAI executor and point its tool registry to the MCP server over SSE. Then we ask it to complete the browser task strictly via tools.



```kotlin
import kotlinx.coroutines.runBlocking

runBlocking {
    println("Connecting to Playwright MCP server...")
    val toolRegistry = McpToolRegistryProvider.fromTransport(
        transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:8931")
    )
    println("Successfully connected to Playwright MCP server")

    // Create the agent
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(openAIApiToken),
        llmModel = OpenAIModels.Chat.GPT4o,
        toolRegistry = toolRegistry,
    )

    val request = "Open a browser, navigate to jetbrains.com, accept all cookies, click AI in toolbar"
    println("Sending request: $request")

    agent.run(
        request + ". " +
            "You can only call tools. Use the Playwright tools to complete this task."
    )
}

```

## 4) Shut down the MCP process
Always clean up the external process at the end of your run.



```kotlin
// Shutdown the Playwright MCP process
println("Closing connection to Playwright MCP server")
process.destroy()

```

## Troubleshooting
- If the agent can't connect, make sure the MCP server is running on `http://localhost:8931`.
- If you don't see the browser, ensure Playwright is installed and able to launch a browser on your system.
- If you get authentication errors from OpenAI, double-check the `OPENAI_API_KEY` environment variable.

## Next steps
- Try different websites or flows. The MCP server exposes a rich set of Playwright tools.
- Swap the LLM model, or add more tools to the Koog agent.
- Integrate this flow into your app, or publish the notebook as documentation.
