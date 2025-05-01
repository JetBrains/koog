package ai.grazie.code.agents.local.feature

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.local.agent.LocalAgentStorageKey
import ai.grazie.code.agents.local.agent.createStorageKey
import ai.grazie.code.agents.local.agent.stage.LocalAgentStageContext
import ai.grazie.code.agents.local.features.AIAgentPipeline
import ai.grazie.code.agents.local.features.KotlinAIAgentFeature
import ai.grazie.code.agents.local.features.config.FeatureConfig
import ai.grazie.code.agents.local.graph.LocalAgentNode
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message

class TestFeature(val events: MutableList<String>) {

    class Config : FeatureConfig() {
        var events: MutableList<String>? = null
    }

    companion object Feature : KotlinAIAgentFeature<Config, TestFeature> {
        override val key: LocalAgentStorageKey<TestFeature> = createStorageKey("test-feature")

        override fun createInitialConfig(): Config = Config()

        override fun install(
            config: Config,
            pipeline: AIAgentPipeline
        ) {
            val feature = TestFeature(events = config.events ?: mutableListOf())

            pipeline.interceptAgentCreated(this, feature) {
                feature.events += "Agent: agent created (strategy name: '${strategy.name}')"

                readStages { stages -> feature.events += "Agent: agent created (strategy name: '${strategy.name}'). read stages (size: ${stages.size})" }
            }

            pipeline.interceptStrategyStarted(this, feature) {
                feature.events += "Agent: strategy started (strategy name: '${strategy.name}')"

                readStages { stages -> feature.events += "Agent: strategy started (strategy name: '${strategy.name}'). read stages (size: ${stages.size})" }
            }

            pipeline.interceptContextStageFeature(this) { stageContext: LocalAgentStageContext ->
                feature.events += "Stage Context: request features from stage context (stage name: ${stageContext.stageName})"
                TestFeature(mutableListOf())
            }

            pipeline.interceptBeforeLLMCall(this, feature) { prompt: Prompt ->
                feature.events += "LLM: start LLM call (prompt: '${prompt.messages.firstOrNull { it.role == Message.Role.User }?.content}')"
            }

            pipeline.interceptAfterLLMCall(this, feature) { response: String ->
                feature.events += "LLM: finish LLM call (response: '$response')"
            }

            pipeline.interceptBeforeLLMCallWithTools(this, feature) { prompt: Prompt, tools: List<ToolDescriptor> ->
                feature.events += "LLM + Tools: start LLM call with tools (prompt: '${prompt.messages.firstOrNull { it.role == Message.Role.User }?.content}', tools: [${tools.joinToString { it.name }}])"
            }

            pipeline.interceptAfterLLMCallWithTools(this, feature) { responses, tools ->
                feature.events += "LLM + Tools: finish LLM call with tools (responses: '$responses', tools: [${tools.joinToString { it.name }}])"
            }

            pipeline.interceptBeforeToolCall(this, feature) { tools ->
                feature.events += "Tool: start tool calls $tools"
            }

            pipeline.interceptAfterToolCall(this, feature) { results ->
                feature.events += "Tool: finish tool calls ${results.map { it.toMessage() }}"
            }

            pipeline.interceptBeforeNode(this, feature) { node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any? ->
                feature.events += "Node: start node (name: '${node.name}', input: '$input')"
            }

            pipeline.interceptAfterNode(this, feature) { node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?, output: Any? ->
                feature.events += "Node: finish node (name: '${node.name}', input: '$input', output: '$output')"
            }
        }
    }
}
