package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.prompt.message.Message

class TestFeature(val events: MutableList<String>, val runIds: MutableList<String>) {

    class TestConfig : FeatureConfig() {
        var events: MutableList<String> = mutableListOf()
        var runIds: MutableList<String> = mutableListOf()
    }

    companion object Feature : AIAgentGraphFeature<TestConfig, TestFeature>, AIAgentFunctionalFeature<TestConfig, TestFeature> {
        override val key: AIAgentStorageKey<TestFeature> = createStorageKey("test-feature")

        override fun createInitialConfig(): TestConfig = TestConfig()

        override fun install(
            config: TestConfig,
            pipeline: AIAgentGraphPipeline,
        ): TestFeature {
            val testFeature = TestFeature(
                events = config.events,
                runIds = config.runIds
            )

            installCommon(pipeline, config)

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

            return testFeature
        }

        override fun install(
            config: TestConfig,
            pipeline: AIAgentFunctionalPipeline
        ): TestFeature {
            val testFeature = TestFeature(
                events = config.events,
                runIds = config.runIds
            )

            installCommon(pipeline, config)
            return testFeature
        }

        //region Private Methods

        private fun installCommon(
            pipeline: AIAgentPipeline,
            config: TestConfig,
        ) {
            pipeline.interceptAgentStarting(this) { eventContext ->
                config.runIds += eventContext.runId
                config.events +=
                    "Agent: before agent started (id: ${eventContext.agent.id}, run id: ${eventContext.runId})"
            }

            pipeline.interceptStrategyStarting(this) { eventContext ->
                config.events +=
                    "Agent: strategy started (strategy name: ${eventContext.strategy.name})"
            }

            pipeline.interceptLLMCallStarting(this) { event ->
                config.events +=
                    "LLM: start LLM call (prompt: ${event.prompt.messages.firstOrNull {
                        it.role == Message.Role.User
                    }?.content}, tools: [${event.tools.joinToString { it.name }}])"
            }

            pipeline.interceptLLMCallCompleted(this) { event ->
                config.events +=
                    "LLM: finish LLM call (responses: [${event.responses.joinToString(", ") {
                        "${it.role.name}: ${it.content}"
                    }}])"
            }

            pipeline.interceptToolCallStarting(this) { event ->
                config.events +=
                    "Tool: call tool (tool: ${event.toolName}, args: ${event.toolArgs})"
            }

            pipeline.interceptToolCallCompleted(this) { event ->
                config.events +=
                    "Tool: finish tool call with result (tool: ${event.toolName}, result: ${event.toolResult?.let { event.toolResult } ?: "null"})"
            }
        }

        //endregion Private Methods
    }
}
