package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind

/**
 * Configuration for terminal tools in a single-run strategy.
 *
 * @property toolNames names of the tools that end the agent run as soon as one of them succeeds
 * @property agentResult maps the terminal [ReceivedToolResult] to the agent's output,
 *   defaults to the output of the tool itself
 */
public data class TerminalToolsConfig(
    val toolNames: Set<String>,
    val agentResult: (ReceivedToolResult) -> String = ReceivedToolResult::output
)

/**
 * Creates a single-run agent strategy in which selected tools end the run.
 *
 * Works like [ai.koog.agents.core.agent.singleRunStrategy], except that when the LLM calls one of
 * [TerminalToolsConfig.toolNames] and that call succeeds, the agent finishes with the tool result
 * instead of sending the result back to the LLM for another round trip. This suits tools that already
 * produce the final answer, such as a submit or report tool, where the closing LLM call would only
 * restate what the tool returned.
 *
 * Only successful results terminate the run. A terminal tool that fails or fails validation is sent
 * back to the LLM like any other tool result, so the model can recover and retry.
 *
 * When several tools are called in one batch, the first successful terminal result wins and the
 * remaining results of that batch are not sent to the LLM.
 *
 * @param config the terminal tool names and how their result becomes the agent output
 * @param parallelTools if true, tools will be executed in parallel, otherwise sequentially
 * @return [AIAgentGraphStrategy] that finishes as soon as a terminal tool succeeds
 */
public fun singleRunStrategyWithTerminalTools(
    config: TerminalToolsConfig,
    parallelTools: Boolean = false,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("single_run_with_terminal_tools") {
    val nodeCallLLM by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTools(parallel = parallelTools)
    val nodeSendToolResult by nodeLLMSendToolResults()

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCalls { true })
    edge(nodeCallLLM forwardTo nodeFinish onTextMessage { true })

    edge(
        nodeExecuteTool forwardTo nodeFinish
            onCondition { it.terminalOutput(config) != null }
            transformed { checkNotNull(it.terminalOutput(config)) }
    )
    edge(nodeExecuteTool forwardTo nodeSendToolResult onCondition { it.terminalOutput(config) == null })

    edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
}

/**
 * Returns the agent output produced by the first successfully executed terminal tool in this batch,
 * or `null` when the batch holds no successful terminal result.
 */
private fun ReceivedToolResults.terminalOutput(config: TerminalToolsConfig): String? = toolResults
    .firstOrNull { it.tool in config.toolNames && it.resultKind is ToolResultKind.Success }
    ?.let(config.agentResult)
