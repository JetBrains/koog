package ai.koog.prompt.executor.model

/**
 * Defines how execution should traverse and retry models returned by a [ModelSelector].
 *
 * @constructor Creates immutable execution policy.
 * @property modelAttemptLimit Maximum number of models returned by [ModelSelector.select] that may be attempted.
 * @property maxRetriesPerModel Maximum retries per attempted model.
 * @property isRecoverable Failure classifier. `true` means execution may continue according to policy.
 * @throws IllegalArgumentException If [maxRetriesPerModel] is negative.
 */
public class ExecutionPolicy(
    public val modelAttemptLimit: ModelAttemptLimit = ModelAttemptLimit.UpTo(1),
    public val maxRetriesPerModel: Int = 0,
    public val isRecoverable: (Throwable) -> Boolean = { true },
) {

    init {
        require(maxRetriesPerModel >= 0) { "maxRetriesPerModel must be non-negative." }
    }

    public companion object {
        /**
         * Default policy: attempt only the top-ranked model with no retries.
         */
        public val Default: ExecutionPolicy = ExecutionPolicy()
    }
}

/**
 * Limit for how many ranked models can be attempted for a single execution.
 */
public sealed interface ModelAttemptLimit {

    /**
     * Attempt all models from selector ranking.
     */
    public object All : ModelAttemptLimit

    /**
     * Attempt up to [count] models from selector ranking.
     *
     * @constructor Creates bounded-attempt limit.
     * @property count Maximum number of models to attempt.
     * @throws IllegalArgumentException If [count] is not positive.
     */
    public data class UpTo(val count: Int) : ModelAttemptLimit {
        init {
            require(count > 0) { "count must be positive, was $count" }
        }
    }
}
