package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * Configuration for conversation history compression in a single-run strategy.
 *
 * @property isHistoryTooBig function that checks the current [Prompt] and returns true
 *   when the message count or token size exceeds a threshold
 * @property compressionStrategy [HistoryCompressionStrategy] implementation that defines
 *   how to compress the conversation history
 * @property retrievalModel Optional [LLModel] to use for compression (defaults to agent's model)
 */
public data class HistoryCompressionConfig(
    val isHistoryTooBig: (Prompt) -> Boolean,
    val compressionStrategy: HistoryCompressionStrategy,
    val retrievalModel: LLModel? = null
)

/**
 * Creates a single-run agent strategy with automatic conversation history compression.
 *
 * Works like [ai.koog.agents.core.agent.singleRunStrategy] but adds a compression step after each tool execution:
 * if the conversation history becomes too large (based on [HistoryCompressionConfig.isHistoryTooBig]),
 * it compresses the message list to essential facts before continuing.
 *
 * @param config specifies when to trigger compression (size threshold), how to compress
 *   (fact extraction strategy), and optionally which model to use for compression
 * @param runMode how tools are executed: [ToolCalls.SINGLE_RUN_SEQUENTIAL] (one tool per LLM call),
 *   [ToolCalls.SEQUENTIAL] (multiple tools per call, executed sequentially), or [ToolCalls.PARALLEL]
 *   (multiple tools per call, executed concurrently)
 * @return [AIAgentGraphStrategy] that compresses conversation history when needed
 */
public fun singleRunStrategyWithHistoryCompression(
    config: HistoryCompressionConfig,
    runMode: ToolCalls = ToolCalls.SINGLE_RUN_SEQUENTIAL
): AIAgentGraphStrategy<String, String> =
    when (runMode) {
        ToolCalls.SEQUENTIAL -> singleRunWithHistoryCompressionParallelAbility(config, false)
        ToolCalls.PARALLEL -> singleRunWithHistoryCompressionParallelAbility(config, true)
        ToolCalls.SINGLE_RUN_SEQUENTIAL -> singleRunWithHistoryCompressionModeStrategy(config)
    }

private fun singleRunWithHistoryCompressionParallelAbility(
    config: HistoryCompressionConfig,
    parallelTools: Boolean
) = strategy("single_run_with_history_compression_sequential") {
    val nodeCallLLM by nodeLLMRequestMultiple()
    val nodeExecuteTool by nodeExecuteMultipleTools(parallelTools = parallelTools)
    val nodeSendToolResult by nodeLLMSendMultipleToolResults()
    val nodeCompressHistory by nodeLLMCompressHistory<List<ReceivedToolResult>>(
        strategy = config.compressionStrategy,
        retrievalModel = config.retrievalModel
    )
    val nodeSendCompressedHistory by node<List<ReceivedToolResult>, List<Message.Response>> {
        llm.writeSession {
            requestLLMMultiple()
        }
    }

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onMultipleToolCalls { true })
    edge(
        nodeCallLLM forwardTo nodeFinish
            onMultipleAssistantMessages { true }
            transformed { it.joinToString("\n") { message -> message.content } }
    )

    edge(nodeExecuteTool forwardTo nodeCompressHistory onCondition { llm.readSession { config.isHistoryTooBig(prompt) } })
    edge(nodeExecuteTool forwardTo nodeSendToolResult onCondition { llm.readSession { !config.isHistoryTooBig(prompt) } })
    edge(nodeCompressHistory forwardTo nodeSendCompressedHistory)

    edge(
        nodeSendToolResult forwardTo nodeFinish
            onMultipleAssistantMessages { true }
            transformed { it.joinToString("\n") { message -> message.content } }
    )

    edge(nodeSendToolResult forwardTo nodeExecuteTool onMultipleToolCalls { true })

    edge(
        nodeSendCompressedHistory forwardTo nodeFinish
            onMultipleAssistantMessages { true }
            transformed { it.joinToString("\n") { message -> message.content } }
    )

    edge(nodeSendCompressedHistory forwardTo nodeExecuteTool onMultipleToolCalls { true })
}

private fun singleRunWithHistoryCompressionModeStrategy(config: HistoryCompressionConfig) = strategy("single_run_with_history_compression") {
    val nodeCallLLM by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()
    val compressHistory by nodeLLMCompressHistory<ReceivedToolResult>(
        strategy = config.compressionStrategy,
        retrievalModel = config.retrievalModel
    )
    val nodeSendCompressedHistory by node<ReceivedToolResult, Message.Response> {
        llm.writeSession {
            requestLLM()
        }
    }

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })

    edge(nodeExecuteTool forwardTo compressHistory onCondition { llm.readSession { config.isHistoryTooBig(prompt) } })
    edge(nodeExecuteTool forwardTo nodeSendToolResult onCondition { llm.readSession { !config.isHistoryTooBig(prompt) } })
    edge(compressHistory forwardTo nodeSendCompressedHistory)

    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })

    edge(nodeSendCompressedHistory forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendCompressedHistory forwardTo nodeExecuteTool onToolCall { true })
}
