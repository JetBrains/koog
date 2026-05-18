package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.asUserMessage
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.prompt.Prompt
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
 * @param parallelTools
 *   (multiple tools per call, executed concurrently)
 * @return [AIAgentGraphStrategy] that compresses conversation history when needed
 */
public fun singleRunStrategyWithHistoryCompression(
    config: HistoryCompressionConfig,
    parallelTools: Boolean = false,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("single_run_with_history_compression") {
    val nodeLLMRequest by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTools(parallel = parallelTools)
    val compressHistory by nodeLLMCompressHistory<Message.User>(
        strategy = config.compressionStrategy,
        retrievalModel = config.retrievalModel
    )

    edge(nodeStart forwardTo nodeLLMRequest asUserMessage { it })
    edge(nodeExecuteTool forwardTo compressHistory onCondition { llm.readSession { config.isHistoryTooBig(prompt) } })
    edge(nodeExecuteTool forwardTo nodeLLMRequest onCondition { llm.readSession { !config.isHistoryTooBig(prompt) } })
    edge(compressHistory forwardTo nodeLLMRequest)
    edge(nodeLLMRequest forwardTo nodeExecuteTool onToolCalls { true })
    edge(nodeLLMRequest forwardTo nodeFinish onTextMessage { true })
}
