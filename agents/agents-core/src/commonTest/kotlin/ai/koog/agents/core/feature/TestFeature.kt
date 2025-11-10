package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.prompt.message.Message

class TestFeature(val events: MutableList<String>, val runIds: MutableList<String>) {

    class Config : FeatureConfig() {
        var events: MutableList<String>? = null
        var runIds: MutableList<String>? = null
    }

    companion object Feature : AIAgentGraphFeature<Config, TestFeature> {
        override val key: AIAgentStorageKey<TestFeature> = createStorageKey("test-feature")

        override fun createInitialConfig(): Config = Config()

        override fun install(
            config: Config,
            pipeline: AIAgentGraphPipeline,
        ): TestFeature {
            val testFeature = TestFeature(
                events = config.events ?: mutableListOf(),
                runIds = config.runIds ?: mutableListOf()
            )

            pipeline.interceptAgentStarting(this) { eventContext ->
                testFeature.runIds += eventContext.runId
                testFeature.events +=
                    "Agent: before agent started (id: ${eventContext.agent.id}, run id: ${eventContext.runId})"
            }

            pipeline.interceptStrategyStarting(this) { eventContext ->
                testFeature.events +=
                    "Agent: strategy started (strategy name: ${eventContext.strategy.name})"
            }

            pipeline.interceptLLMCallStarting(this) { event ->
                testFeature.events +=
                    "LLM: start LLM call (prompt: ${event.prompt.messages.firstOrNull {
                        it.role == Message.Role.User
                    }?.content}, tools: [${event.tools.joinToString { it.name }}])"
            }

            pipeline.interceptLLMCallCompleted(this) { event ->
                testFeature.events +=
                    "LLM: finish LLM call (responses: [${event.responses.joinToString(", ") {
                        "${it.role.name}: ${it.content}"
                    }}])"
            }

            pipeline.interceptNodeExecutionStarting(this) { event ->
                testFeature.events +=
                    "Node: start node (name: ${event.node.name}, input: ${event.input})"
            }

            pipeline.interceptNodeExecutionCompleted(this) { event ->
                testFeature.events +=
                    "Node: finish node (name: ${event.node.name}, input: ${event.input}, output: ${event.output})"
            }

            pipeline.interceptNodeExecutionFailed(this) { event ->
                testFeature.events +=
                    "Node: execution error (name: ${event.node.name}, error: ${event.throwable.message})"
            }

            pipeline.interceptSubgraphExecutionStarting(this) { event ->
                testFeature.events +=
                    "Subgraph: start subgraph (name: ${event.subgraph.name}, input: ${event.input})"
            }

            pipeline.interceptSubgraphExecutionCompleted(this) { event ->
                testFeature.events +=
                    "Subgraph: finish subgraph (name: ${event.subgraph.name}, input: ${event.input}, output: ${event.output})"
            }

            pipeline.interceptSubgraphExecutionFailed(this) { event ->
                testFeature.events +=
                    "Subgraph: execution error (name: ${event.subgraph.name}, error: ${event.throwable.message})"
            }

            pipeline.interceptToolCallStarting(this) { event ->
                testFeature.events +=
                    "Tool: call tool (tool: ${event.tool.name}, args: ${event.toolArgs})"
            }

            pipeline.interceptToolCallCompleted(this) { event ->
                testFeature.events +=
                    "Tool: finish tool call with result (tool: ${event.tool.name}, result: ${event.result?.let(event.tool::encodeResultToStringUnsafe) ?: "null"})"
            }

            return testFeature
        }
    }
}
