package ai.koog.agents.longtermmemory.ingestion

import ai.koog.agents.core.annotation.ExperimentalAgentsApi

/**
 * Defines when messages should be extracted and ingested into the memory repository.
 */
@ExperimentalAgentsApi
public enum class IngestionTiming {
    /**
     * Ingest each message as soon as the LLM call completes.
     * Enables intra-session RAG and provides crash resilience.
     */
    ON_LLM_CALL,

    /**
     * Ingest all messages at once when the agent run completes.
     * Enables holistic extraction/summarization and avoids critical-path latency.
     */
    ON_AGENT_COMPLETION,
}
