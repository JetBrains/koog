package ai.koog.prompt.executor.clients.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for retry behavior in LLM client operations.
 *
 * @property maxAttempts Maximum number of attempts (including initial)
 * @property initialDelay Initial delay before first retry
 * @property maxDelay Maximum delay between retries
 * @property backoffMultiplier Multiplier for exponential backoff
 * @property jitterFactor Random jitter factor (0.0 to 1.0)
 * @property retryablePatterns Patterns to identify retryable errors
 * @property retryAfterExtractor Optional extractor for retry-after hints
 */
public data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = 1.seconds,
    val maxDelay: Duration = 30.seconds,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1,
    val retryablePatterns: List<RetryablePattern> = DEFAULT_PATTERNS,
    val retryAfterExtractor: RetryAfterExtractor? = DefaultRetryAfterExtractor
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be at least 1.0" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be between 0.0 and 1.0" }
        require(initialDelay <= maxDelay) { "initialDelay ($initialDelay) must not be greater than maxDelay ($maxDelay)" }
    }

    /**
     * Companion object for providing predefined retry configurations and patterns.
     * Contains default retry logic settings that can be used across different use cases,
     * along with scoped configurations for conservative, aggressive, and production environments.
     */
    public companion object {
        /**
         * Default retry patterns that work across all providers.
         */
        public val DEFAULT_PATTERNS: List<RetryablePattern> = listOf(
            // HTTP status codes
            RetryablePattern.Status(429), // Rate limit
            RetryablePattern.Status(500), // Internal server error
            RetryablePattern.Status(502), // Bad gateway
            RetryablePattern.Status(503), // Service unavailable
            RetryablePattern.Status(504), // Gateway timeout
            RetryablePattern.Status(529), // Anthropic overloaded

            // Error keywords
            RetryablePattern.Keyword("rate limit"),
            RetryablePattern.Keyword("too many requests"),
            RetryablePattern.Keyword("overloaded"),
            RetryablePattern.Keyword("request timeout"),
            RetryablePattern.Keyword("connection timeout"),
            RetryablePattern.Keyword("read timeout"),
            RetryablePattern.Keyword("write timeout"),
            RetryablePattern.Keyword("connection reset by peer"),
            RetryablePattern.Keyword("connection refused"),
            RetryablePattern.Keyword("temporarily unavailable"),
            RetryablePattern.Keyword("service unavailable")
        )

        /**
         * Conservative configuration - fewer retries, longer delays.
         */
        public val CONSERVATIVE: RetryConfig = RetryConfig(
            maxAttempts = 3,
            initialDelay = 2.seconds,
            maxDelay = 30.seconds
        )

        /**
         * Aggressive configuration - more retries, shorter delays.
         */
        public val AGGRESSIVE: RetryConfig = RetryConfig(
            maxAttempts = 5,
            initialDelay = 500.milliseconds,
            maxDelay = 20.seconds,
            backoffMultiplier = 1.5
        )

        /**
         * Production configuration - balanced for production use.
         */
        public val PRODUCTION: RetryConfig = RetryConfig(
            maxAttempts = 3,
            initialDelay = 1.seconds,
            maxDelay = 20.seconds,
            backoffMultiplier = 2.0,
            jitterFactor = 0.2
        )

        /**
         * No retry - effectively disables retry logic.
         */
        public val DISABLED: RetryConfig = RetryConfig(maxAttempts = 1)
    }
}

/**
 * Pattern for identifying retryable errors.
 */
public sealed class RetryablePattern {
    /**
     * Evaluates whether the given message matches the criteria defined by the implementing class.
     *
     * @param message The message to evaluate against the matching criteria.
     * @return `true` if the message matches the criteria, otherwise `false`.
     */
    public abstract fun matches(message: String): Boolean

    /**
     * Matches HTTP status codes in error messages.
     */
    public data class Status(val code: Int) : RetryablePattern() {
        private val patterns = listOf(
            Regex("\\b$code\\b"),
            Regex("status:?\\s*$code"),
            Regex("error:?\\s*$code", RegexOption.IGNORE_CASE)
        )

        override fun matches(message: String): Boolean =
            patterns.any { it.containsMatchIn(message) }
    }

    /**
     * Matches keywords in error messages.
     */
    public data class Keyword(val keyword: String) : RetryablePattern() {
        override fun matches(message: String): Boolean =
            keyword.lowercase() in message.lowercase()
    }

    /**
     * Matches using a custom regex.
     */
    public data class Regex(val pattern: kotlin.text.Regex) : RetryablePattern() {
        override fun matches(message: String): Boolean =
            pattern.containsMatchIn(message)
    }

    /**
     * Custom matching logic.
     */
    public class Custom(private val matcher: (String) -> Boolean) : RetryablePattern() {
        override fun matches(message: String): Boolean = matcher(message)
    }
}

/**
 * Extracts retry-after hints from error messages.
 */
public fun interface RetryAfterExtractor {
    /**
     * Extracts a retry-after duration from the provided error message.
     *
     * @param message The error message from which to extract the retry-after duration.
     * @return The extracted retry-after duration, or null if no valid duration could be determined.
     */
    public fun extract(message: String): Duration?
}

/**
 * Default implementation that extracts common retry-after patterns.
 */
public object DefaultRetryAfterExtractor : RetryAfterExtractor {
    private val patterns = listOf(
        Regex("retry\\s+after\\s+(\\d+)\\s+second", RegexOption.IGNORE_CASE),
        Regex("retry-after:\\s*(\\d+)", RegexOption.IGNORE_CASE),
        Regex("wait\\s+(\\d+)\\s+second", RegexOption.IGNORE_CASE)
    )

    override fun extract(message: String): Duration? {
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                match.groupValues.getOrNull(1)?.toLongOrNull()?.let { seconds ->
                    return seconds.seconds
                }
            }
        }
        return null
    }
}
