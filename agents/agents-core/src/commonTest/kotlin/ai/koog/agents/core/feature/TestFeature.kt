package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.prompt.message.Message

class TestFeature(val events: MutableList<String>) {

    class TestConfig : FeatureConfig() {
        var events: MutableList<String> = mutableListOf()
        var runIds: MutableList<String> = mutableListOf()

        fun addEvent(eventContext: AgentLifecycleEventContext, parameters: Map<String, Any?>) {
            val eventStringBuilder = StringBuilder("${eventContext.eventType::class.simpleName} ")
                .append("(")
                .append("path: ").append(eventContext.executionInfo.path())

            if (parameters.isNotEmpty()) {
                eventStringBuilder
                    .append(", ")
                    .append(parameters.entries.joinToString(", ") { "${it.key}: ${it.value}" })
            }

            eventStringBuilder.append(")")
            events += eventStringBuilder.toString()
        }
    }

    companion object Feature : AIAgentGraphFeature<TestConfig, TestFeature>, AIAgentFunctionalFeature<TestConfig, TestFeature> {
        override val key: AIAgentStorageKey<TestFeature> = createStorageKey("test-feature")

        override fun createInitialConfig(): TestConfig = TestConfig()

        override fun install(
            config: TestConfig,
            pipeline: AIAgentGraphPipeline,
        ): TestFeature {
            val testFeature = TestFeature(
                events = config.events
            )

            installCommon(pipeline, config)

            pipeline.interceptNodeExecutionStarting(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "name" to event.node.name,
                        "input" to event.input
                    )
                )
            }

            pipeline.interceptNodeExecutionCompleted(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "name" to event.node.name,
                        "input" to event.input,
                        "output" to event.output
                    )
                )
            }

            pipeline.interceptNodeExecutionFailed(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "name" to event.node.name,
                        "error" to event.throwable.message
                    )
                )
            }

            pipeline.interceptSubgraphExecutionStarting(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "name" to event.subgraph.name,
                        "input" to event.input
                    )
                )
            }

            pipeline.interceptSubgraphExecutionCompleted(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "name" to event.subgraph.name,
                        "input" to event.input,
                        "output" to event.output
                    )
                )
            }

            pipeline.interceptSubgraphExecutionFailed(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "name" to event.subgraph.name,
                        "error" to event.throwable.message
                    )
                )
            }

            return testFeature
        }

        override fun install(
            config: TestConfig,
            pipeline: AIAgentFunctionalPipeline
        ): TestFeature {
            val testFeature = TestFeature(
                events = config.events
            )

            installCommon(pipeline, config)
            return testFeature
        }

        //region Private Methods

        private fun installCommon(
            pipeline: AIAgentPipeline,
            config: TestConfig,
        ) {
            pipeline.interceptAgentStarting(this) { event ->
                config.runIds += event.runId
                config.addEvent(
                    event,
                    mapOf(
                        "id" to event.agent.id,
                        "run id" to event.runId
                    )
                )
            }

            pipeline.interceptAgentCompleted(this) { event ->
                config.runIds += event.runId
                config.addEvent(
                    event,
                    mapOf(
                        "id" to event.agentId,
                        "run id" to event.runId,
                        "result" to event.result
                    )
                )
            }

            pipeline.interceptAgentClosing(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "id" to event.agentId
                    )
                )
            }

            pipeline.interceptStrategyStarting(this) { event ->
                config.addEvent(event, mapOf("strategy" to event.strategy.name))
            }

            pipeline.interceptLLMCallStarting(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "prompt" to event.prompt.messages.lastOrNull { it.role == Message.Role.User }?.content,
                        "tools" to "[${event.tools.joinToString { it.name }}]"
                    )
                )
            }

            pipeline.interceptLLMCallCompleted(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "responses" to "[${event.responses.joinToString(", ") { "${it.role.name}: ${it.content}" }}]"
                    )
                )
            }

            pipeline.interceptToolCallStarting(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "tool" to event.toolName,
                        "args" to event.toolArgs
                    )
                )
            }

            pipeline.interceptToolCallCompleted(this) { event ->
                config.addEvent(
                    event,
                    mapOf(
                        "tool" to event.toolName,
                        "result" to (event.toolResult ?: "null")
                    )
                )
            }
        }

        //endregion Private Methods
    }
}
