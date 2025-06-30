package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
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
            val context = InterceptContext(this, feature)

            pipeline.interceptBeforeAgentStarted(context) {
                feature.events += "Agent: before agent started"
                readStrategy { strategy -> feature.events += "Agent: before agent started (strategy name: ${strategy.name})" }
            }

            pipeline.interceptStrategyStarted(context) {
                feature.events += "Agent: strategy started (strategy name: ${strategy.name})"
            }

            pipeline.interceptContextAgentFeature(this) { agentContext: AIAgentContextBase ->
                feature.events += "Agent Context: request features from agent context"
                TestFeature(mutableListOf())
            }

            pipeline.interceptBeforeLLMCall(context) { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionId: String ->
                feature.events += "LLM: start LLM call (prompt: ${prompt.messages.firstOrNull { it.role == Message.Role.User }?.content}, tools: [${tools.joinToString { it.name }}])"
            }

            pipeline.interceptAfterLLMCall(context) { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>, sessionId: String ->
                feature.events += "LLM: finish LLM call (responses: [${responses.joinToString(", ") { "${it.role.name}: ${it.content}" }}])"
            }

            pipeline.interceptBeforeNode(context) { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
                feature.events += "Node: start node (name: ${node.name}, input: $input)"
            }

            pipeline.interceptAfterNode(context) { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? ->
                feature.events += "Node: finish node (name: ${node.name}, input: $input, output: $output)"
            }

            pipeline.interceptToolCall(context) { tool, toolArgs ->
                feature.events += "Tool: call tool (tool: ${tool.name}, args: $toolArgs)"
            }

            pipeline.interceptToolCallResult(context) { tool, toolArgs, result ->
                feature.events += "Tool: finish tool call with result (tool: ${tool.name}, result: ${result?.toStringDefault() ?: "null"})"
            }
        }
    }
}
