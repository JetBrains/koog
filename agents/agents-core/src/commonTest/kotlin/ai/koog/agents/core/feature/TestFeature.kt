package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

class TestFeature(val events: MutableList<String>) {

    class Config : FeatureConfig() {
        var events: MutableList<String>? = null
    }

    companion object Feature : AIAgentFeature<Config, TestFeature> {
        override val key: AIAgentStorageKey<TestFeature> = createStorageKey("test-feature")

        override fun createInitialConfig(): Config = Config()

        override fun install(
            config: Config,
            pipeline: AIAgentPipeline
        ) {
            val feature = TestFeature(events = config.events ?: mutableListOf())

            pipeline.interceptBeforeAgentStarted(this, feature) {
                feature.events += "Agent: before agent started"
                readStrategy { strategy -> feature.events += "Agent: before agent started (strategy name: '${strategy.name}')" }
            }

            pipeline.interceptStrategyStarted(this, feature) {
                feature.events += "Agent: strategy started (strategy name: '${strategy.name}')"
            }

            pipeline.interceptContextAgentFeature(this) { agentContext: AIAgentContextBase ->
                feature.events += "Agent Context: request features from agent context"
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

            pipeline.interceptBeforeNode(this, feature) { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
                feature.events += "Node: start node (name: '${node.name}', input: '$input')"
            }

            pipeline.interceptAfterNode(this, feature) { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? ->
                feature.events += "Node: finish node (name: '${node.name}', input: '$input', output: '$output')"
            }

            pipeline.interceptToolCall(this, feature) { tool, toolArgs ->
                feature.events += "Tool: call tool (tool: ${tool.name}, args: $toolArgs)"
            }

            pipeline.interceptToolCallResult(this, feature) { tool, toolArgs, result ->
                feature.events += "Tool: finish tool call with result (tool: ${tool.name}, result: ${result?.toStringDefault() ?: "null"})"
            }
        }
    }
}
