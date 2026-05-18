@file:JvmName("AIAgentSimpleStrategies")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Creates a single-run strategy for an AI agent.
 * This strategy defines a simple execution flow where the agent processes input,
 * calls tools, and sends results back to the agent.
 * The flow consists of the following steps:
 * 1. Start the agent.
 * 2. Call the LLM with the input.
 * 3. Execute a tool based on the LLM's response.
 * 4. Send the tool result back to the LLM.
 * 5. Repeat until LLM indicates no further tool calls are needed or the agent finishes.
 * @param parallelTools if true, tools will be executed in parallel, otherwise sequentially
 * @return An instance of AIAgentStrategy configured according to the specified run mode.
 */
@JvmOverloads
public fun singleRunStrategy(parallelTools: Boolean = false): AIAgentGraphStrategy<String, String> = strategy<String, String>("single_run") {
    val nodeCallLLM by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTools()
    val nodeSendToolResult by nodeLLMSendToolResults()

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCalls { true })
    edge(nodeCallLLM forwardTo nodeFinish onTextMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
}
