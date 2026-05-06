package ai.koog.agents.longtermmemory.feature

/**
 * Policy controlling how failures from the underlying memory storage are handled
 * by the [LongTermMemory] feature.
 *
 * Failures caused by [kotlin.coroutines.cancellation.CancellationException] are always
 * propagated regardless of the selected policy.
 */
public enum class FailurePolicy {
    /**
     * Re-throw the underlying error wrapped into a dedicated [LongTermMemory] exception
     * (either [LongTermMemoryRetrievalException] or [LongTermMemoryIngestionException]).
     *
     * For retrieval, this stops the LLM call before it is executed, which is usually
     * safer than answering without the required memory context.
     *
     * For ingestion, this aborts the agent run instead of silently dropping memory records,
     * which is useful for durable audit/logging use cases where losing memory is worse
     * than failing the run.
     */
    FAIL_FAST,

    /**
     * Log the error and continue. For retrieval this means proceeding to the LLM call
     * with no augmentation (treated as "no relevant memories"). For ingestion this means
     * the records are dropped.
     */
    LOG_AND_CONTINUE,
}

/**
 * Thrown when retrieval from the [LongTermMemory] storage fails and the configured
 * [FailurePolicy] is [FailurePolicy.FAIL_FAST].
 */
public class LongTermMemoryRetrievalException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)

/**
 * Thrown when ingestion into the [LongTermMemory] storage fails and the configured
 * [FailurePolicy] is [FailurePolicy.FAIL_FAST].
 */
public class LongTermMemoryIngestionException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)
