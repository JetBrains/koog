package ai.koog.agents.longtermmemory.ingestion


/**
 * Defines when messages are extracted and ingested into the memory repository, and — equally
 * importantly — defines the *message window* the configured `ExtractionStrategy` is invoked on.
 *
 * The timing controls both *when* the extractor runs and *what* it sees. The extractor must
 * not be relied upon for cross-call deduplication; that is the responsibility of the long-term
 * memory feature itself.
 */
public enum class IngestionTiming {
    /**
     * Call-delta ingestion.
     *
     * The extractor is invoked at each LLM interaction boundary (before the call starts and
     * after the call/stream completes) and only ever sees messages that belong to the
     * *current* interaction and that have not been ingested yet during this agent run:
     *
     *  - Before each LLM call: only the new prompt messages added since the previous
     *    ingestion (typically the new user/system messages of the current turn).
     *  - After each LLM call: only the responses produced by that call.
     *  - After stream completion: only the responses materialised from the current stream.
     *
     * Messages that already appear in the prompt history from earlier calls in the same run
     * are *not* re-presented to the extractor. This makes ON_LLM_CALL safe to use without
     * any extractor-level deduplication tricks.
     *
     * Suitable for intra-session RAG and crash resilience.
     */
    ON_LLM_CALL,

    /**
     * Whole-history ingestion.
     *
     * The extractor is invoked exactly once when the agent run completes successfully, and
     * receives the final accumulated session prompt/history as a single batch.
     *
     * Suitable for holistic extraction/summarisation and for avoiding ingestion latency on
     * the critical path.
     */
    ON_AGENT_COMPLETION,
}
