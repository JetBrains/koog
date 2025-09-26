package ai.koog.agents.example.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * The entry point of the program orchestrating tool interaction and AI-driven operations.
 *
 * This function initializes a Docker-based Google Maps MCP server, sets up tool integration,
 * and defines an AI agent workflow for interacting with Unity3D tools. It demonstrates the
 * following key operations:
 *
 * 1. Starts the MCP server using a subprocess.
 * 2. Configures a registry of tools from the MCP server via transport communication.
 * 3. Defines an agent strategy leveraging OpenAI's GPT model to generate and execute tasks.
 * 4. Runs the agent to perform a specified task (e.g., creating a scene in Unity without scripts
 *    or play mode).
 * 5. Cleans up by shutting down the Docker container after execution.
 *
 * This function is intended for advanced AI-driven scenarios requiring tool integration
 * and task automation.
 */
fun main() {
    // https://github.com/justinpbarnett/unity-mcp
//    val pathToUnityServer= "path/to/unity/server"
//    val process = ProcessBuilder(
//        "uv", "--directory",
//        pathToUnityServer,
//        "run",
//        "server.py"
//    ).start()

    // https://github.com/IvanMurzak/Unity-MCP
    val pathToUnityProject = "path/to/unity/project"
    val process = ProcessBuilder(
        "$pathToUnityProject/com.ivanmurzak.unity.mcp.server/bin~/Release/net9.0/com.IvanMurzak.Unity.MCP.Server",
        "60606"
    ).start()

    // Wait for the server to start
    Thread.sleep(2000)

    try {
        runBlocking {
            // Create the ToolRegistry with tools from the MCP server
            val toolRegistry = McpToolRegistryProvider.fromTransport(
                transport = McpToolRegistryProvider.defaultStdioTransport(process)
            )

            toolRegistry.tools.forEach {
                println(it.name)
                println(it.descriptor)
            }

            val agentConfig = AIAgentConfig(
                prompt = prompt("cook_agent_system_prompt") {
                    system {
                        "Your are a unity assistant. You can exucute different tasks by interacting with the tools from Unity3d engine."
                    }
                },
                model = OpenAIModels.Chat.GPT4o,
                maxAgentIterations = 1000
            )

            val token = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable not set")
            val executor = simpleOpenAIExecutor(token)

            val strategy = strategy<String, String>("unity_interaction") {
                val nodePlanIngredients by nodeLLMRequest(allowToolCalls = false)
                val interactionWithUnity by subgraphWithTask<String, String>(
                    // work with plan
                    tools = toolRegistry.tools,
                ) { input ->
                    "Start interact with Unity according to the plan: $input"
                }

                edge(
                    nodeStart forwardTo nodePlanIngredients transformed {
                        "Create detailed plan for $agentInput" +
                            "unsing next tools: ${toolRegistry.tools.joinToString("\n") {
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
                        onBeforeAgentStarted { eventContext ->
                            println("OnBeforeAgentStarted first (strategy: ${strategy.name})")
                        }

                        onBeforeAgentStarted { eventContext ->
                            println("OnBeforeAgentStarted second (strategy: ${strategy.name})")
                        }

                        onAgentFinished { eventContext ->
                            println(
                                "OnAgentFinished (agent id: ${eventContext.agentId}, result: ${eventContext.result})"
                            )
                        }
                    }
                }
            )

            val run = agent.run(
                " extend current opened scene for the towerdefence game. " +
                    "Add more placements for the towers, change the path for the enemies"
            )
            println(run)
        }
    } finally {
        // Shutdown the Docker container
        process.destroy()
    }
}
