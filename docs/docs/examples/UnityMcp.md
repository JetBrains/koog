# Unity + Koog: Drive your game from a Kotlin Agent

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/UnityMcp.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/UnityMcp.ipynb
){ .md-button }

This notebook walks you through building a Unity-savvy AI agent with Koog using the Model Context Protocol (MCP). We'll connect to a Unity MCP server, discover tools, plan with an LLM, and execute actions against your open scene.

> Prerequisites
> - A Unity project with the Unity-MCP server plugin installed
> - JDK 17+
> - An OpenAI API key in the OPENAI_API_KEY environment variable



```kotlin
%useLatestDescriptors
%use koog

```


```kotlin
lateinit var process: Process

```

## 1) Provide your OpenAI API key
We read the API key from the `OPENAI_API_KEY` environment variable so you can keep secrets out of the notebook.



```kotlin
val token = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable not set")
val executor = simpleOpenAIExecutor(token)
```

## 2) Configure the Unity agent
We define a compact system prompt and agent settings for Unity.



```kotlin
val agentConfig = AIAgentConfig(
    prompt = prompt("cook_agent_system_prompt") {
        system {
            "You are a Unity assistant. You can execute different tasks by interacting with tools from the Unity engine."
        }
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 1000
)
```


```kotlin

```

## 3) Start the Unity MCP server
We'll launch the Unity MCP server from your Unity project directory and connect over stdio.



```kotlin
// https://github.com/IvanMurzak/Unity-MCP
val pathToUnityProject = "path/to/unity/project"
val process = ProcessBuilder(
    "$pathToUnityProject/com.ivanmurzak.unity.mcp.server/bin~/Release/net9.0/com.IvanMurzak.Unity.MCP.Server",
    "60606"
).start()
```

## 4) Connect from Koog and run the agent
We discover tools from the Unity MCP server, build a small plan-first strategy, and run an agent that uses only tools to modify your open scene.



```kotlin
import kotlinx.coroutines.runBlocking

runBlocking {
    // Create the ToolRegistry with tools from the MCP server
    val toolRegistry = McpToolRegistryProvider.fromTransport(
        transport = McpToolRegistryProvider.defaultStdioTransport(process)
    )

    toolRegistry.tools.forEach {
        println(it.name)
        println(it.descriptor)
    }

    val strategy = strategy<String, String>("unity_interaction") {
        val nodePlanIngredients by nodeLLMRequest(allowToolCalls = false)
        val interactionWithUnity by subgraphWithTask<String, String>(
            // work with plan
            tools = toolRegistry.tools,
        ) { input ->
            "Start interacting with Unity according to the plan: $input"
        }

        edge(
            nodeStart forwardTo nodePlanIngredients transformed {
                "Create detailed plan for " + agentInput + "" +
                    "using the following tools: ${toolRegistry.tools.joinToString("\n") {
                        it.name + "\ndescription:" + it.descriptor
                    }}"
            }
        )
        edge(nodePlanIngredients forwardTo interactionWithUnity onAssistantMessage { true })
        edge(interactionWithUnity forwardTo nodeFinish)
    }

    val agent = AIAgent(
        promptExecutor = executor,
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        installFeatures = {
            install(Tracing)

            install(EventHandler) {
                onAgentStarting { eventContext ->
                    println("OnAgentStarting first (strategy: ${strategy.name})")
                }

                onAgentStarting { eventContext ->
                    println("OnAgentStarting second (strategy: ${strategy.name})")
                }

                onAgentCompleted { eventContext ->
                    println(
                        "OnAgentCompleted (agent id: ${eventContext.agentId}, result: ${eventContext.result})"
                    )
                }
            }
        }
    )

    val result = agent.run(
        " extend current opened scene for the towerdefence game. " +
            "Add more placements for the towers, change the path for the enemies"
    )

    result
}
```

## 5) Shut down the MCP process
Always clean up the external Unity MCP server process at the end of your run.



```kotlin
// Shutdown the Unity MCP process
process.destroy()
```
