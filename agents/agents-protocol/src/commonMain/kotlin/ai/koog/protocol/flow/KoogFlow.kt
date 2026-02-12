package ai.koog.protocol.flow

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowDataType
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgent
import ai.koog.protocol.tool.FlowTool
import ai.koog.protocol.transition.FlowTransition
import kotlin.reflect.typeOf

/**
 * Koog-specific implementation of the Flow interface with agent orchestration.
 */
public class KoogFlow(
    override val id: String,
    override val agents: List<FlowAgent>,
    override val tools: List<FlowTool>,
    override val transitions: List<FlowTransition>,
    public val promptExecutor: PromptExecutor? = null
) : Flow {

    /**
     * Runs the flow with the provided input.
     *
     * @param input The input to pass to the first agent in the flow.
     * @return The output from the final agent in the flow.
     */
    override suspend fun run(input: FlowDataType?): FlowDataType {
        val agent = buildAgent()
        val agentInput = input
            ?: FlowUtil.getFirstAgentOrNull(agents, transitions)?.let { firstAgent -> getInputFromFlowAgent(agent = firstAgent) }
            ?: error("No agents found")

        return agent.run(agentInput)
    }

    //region Private Methods

    private fun getInputFromFlowAgent(agent: FlowAgent): FlowDataType? {
        return when (agent) {
            is FlowTaskAgent -> FlowDataType.FlowString(agent.parameters.task)
            is FlowVerifyAgent -> FlowDataType.FlowString(agent.parameters.task)
            else -> null
        }
    }

    private suspend fun buildAgent(): GraphAIAgent<FlowDataType, FlowDataType> {
        val promptExecutor = promptExecutor ?: buildPromptExecutor(agents)
        val toolRegistry = buildToolRegistry()
        val strategy = buildStrategy(agents, transitions, toolRegistry)
        val model = buildModel()

        val firstAgent = FlowUtil.getFirstAgentOrNull(agents, transitions)
        val agentPrompt = prompt(id = "koog-flow-$id") {
            firstAgent?.prompt?.system?.let { systemPrompt ->
                system(systemPrompt)
            }
        }

        // Calculate a reasonable default for maxAgentIterations based on the number of agents
        // Each agent subgraph can use multiple iterations (setup, call, decide, tools, finalize, etc.)
        val defaultMaxIterations = (agents.size * 10).coerceAtLeast(50)

        val agentConfig = AIAgentConfig(
            prompt = agentPrompt,
            model = model,
            maxAgentIterations = firstAgent?.config?.maxIterations ?: defaultMaxIterations,
        )

        return GraphAIAgent(
            id = "koog-flow-agent-$id",
            inputType = typeOf<FlowDataType>(),
            outputType = typeOf<FlowDataType>(),
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = toolRegistry,
        ) {
            handleEvents {
                onNodeExecutionStarting { ctx ->
                    println("> Node: ${ctx.node.id}")
                }

                onToolCallStarting { ctx ->
                    println("> Tool start\nTool: ${ctx.toolName}, args: ${ctx.toolArgs}")
                }

                onToolCallCompleted { ctx ->
                    println("> Tool completed\nTool: ${ctx.toolName}, args: ${ctx.toolArgs}")
                }

                onLLMCallStarting { ctx ->
                    println(
                        "> LLM start\nRequest:${ctx.prompt.messages.lastOrNull()?.content}\n" +
                            "tools: ${ctx.tools.joinToString("\n") { " - ${it.name }" } }"
                    )
                }

                onLLMCallCompleted { ctx ->
                    println(
                        "> LLM complete\nResponses:${ctx.responses.joinToString("\n") { " - [${it.role.name}] ${it.content}" } } }"
                    )
                }
            }
        }
    }

    private fun buildPromptExecutor(agents: List<FlowAgent>): PromptExecutor {
        val models = agents.map { agent -> agent.model }.distinct().map { modelName ->
            KoogPromptExecutorFactory.resolveModel(modelName)
        }

        return KoogPromptExecutorFactory.buildFromModels(models)
            ?: error("Unable to build PromptExecutor from provided models: $models")
    }

    private suspend fun buildToolRegistry(): ToolRegistry {
        if (tools.isEmpty()) {
            return ToolRegistry.EMPTY
        }

        // Collect all MCP tool registries
        val mcpToolRegistries: List<ToolRegistry> = tools.filterIsInstance<FlowTool.Mcp>().map { mcpTool ->
            when (mcpTool) {
                is FlowTool.Mcp.SSE -> {
                    val transport = McpToolRegistryProvider.defaultSseTransport(mcpTool.url)
                    McpToolRegistryProvider.fromTransport(transport)
                }
                is FlowTool.Mcp.Stdio -> {
                    // Stdio transport requires platform-specific implementation (JVM only)
                    // For now, we skip stdio tools in common code
                    // The JVM-specific implementation should be provided via expect/actual
                    ToolRegistry.EMPTY
                }
            }
        }

        // Merge all tool registries
        return if (mcpToolRegistries.isEmpty()) {
            ToolRegistry.EMPTY
        } else {
            mcpToolRegistries.reduce { acc, registry -> acc + registry }
        }
    }

    private fun buildStrategy(
        agents: List<FlowAgent>,
        transitions: List<FlowTransition>,
        toolRegistry: ToolRegistry,
    ): AIAgentGraphStrategy<FlowDataType, FlowDataType> =
        KoogStrategyFactory.buildStrategy(
            id = "koog-flow-strategy-$id",
            agents = agents,
            transitions = transitions,
            toolRegistry = toolRegistry
        )

    private fun buildModel() = agents.firstOrNull()?.let { agent ->
        KoogPromptExecutorFactory.resolveModel(agent.model)
    } ?: error("No agents found to determine model")

    //endregion Private Methods
}
