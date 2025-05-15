package ai.grazie.code.agents.core.feature

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.agent.entity.AIAgentStorageKey
import ai.grazie.code.agents.core.agent.entity.createStorageKey
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.local.features.common.config.FeatureConfig
import ai.grazie.code.agents.core.agent.entity.AIAgentNodeBase
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message

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
                feature.events += "Agent: before agent started (strategy name: '${strategy.name}')"

                readStages { stages -> feature.events += "Agent: before agent started (strategy name: '${strategy.name}'). read stages (size: ${stages.size})" }
            }

            pipeline.interceptStrategyStarted(this, feature) {
                feature.events += "Agent: strategy started (strategy name: '${strategy.name}')"

                readStages { stages -> feature.events += "Agent: strategy started (strategy name: '${strategy.name}'). read stages (size: ${stages.size})" }
            }

            pipeline.interceptContextStageFeature(this) { stageContext: AIAgentStageContextBase ->
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

            pipeline.interceptBeforeNode(this, feature) { node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any? ->
                feature.events += "Node: start node (name: '${node.name}', input: '$input')"
            }

            pipeline.interceptAfterNode(this, feature) { node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any?, output: Any? ->
                feature.events += "Node: finish node (name: '${node.name}', input: '$input', output: '$output')"
            }

            pipeline.interceptToolCall(this, feature) { stage, tool, toolArgs ->
                feature.events += "Tool: call tool (tool: ${tool.name}, args: $toolArgs)"
            }

            pipeline.interceptToolCallResult(this, feature) { stage, tool, toolArgs, result ->
                feature.events += "Tool: finish tool call with result (tool: ${tool.name}, result: ${result?.toStringDefault() ?: "null"})"
            }
        }
    }
}
