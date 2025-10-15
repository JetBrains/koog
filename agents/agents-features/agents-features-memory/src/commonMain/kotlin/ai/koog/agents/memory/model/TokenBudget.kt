package ai.koog.agents.memory.model

/**
 * Configuration describing how many facts and tokens can be injected into the LLM context.
 *
 * The defaults favour conservative limits while preserving backward compatibility (null means no limit).
 *
 * @property maxTokens Maximum approximate tokens permitted for the injected facts.
 * @property maxFacts Maximum number of facts injected. Evaluation happens before token counting.
 */
public data class TokenBudget(
    val maxTokens: Int = 800,
    val maxFacts: Int = 24
) {
    public companion object {
        /**
         * Budget with no limits.
         */
        public val Unlimited: TokenBudget = TokenBudget(Int.MAX_VALUE, Int.MAX_VALUE)
    }
}
