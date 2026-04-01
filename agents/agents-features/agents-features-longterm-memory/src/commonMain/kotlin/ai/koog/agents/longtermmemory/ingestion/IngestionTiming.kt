package ai.koog.agents.longtermmemory.ingestion

import ai.koog.agents.core.annotation.ExperimentalAgentsApi

/**
 * Defines when messages should be extracted and ingested into the memory repository.
 */
@ExperimentalAgentsApi
public enum class IngestionTiming {
    /**
     * Prompt messages are ingested before each LLM call starts, and the assistant output
     * is ingested after the call completes (or after stream completion for streaming calls).
     * Enables intra-session RAG and provides crash resilience.
     */
    ON_LLM_CALL,

    /**
     * The final accumulated session prompt/history is ingested once at agent completion.
     * Enables holistic extraction/summarization and avoids critical-path latency.
     */
    ON_AGENT_COMPLETION,
}
