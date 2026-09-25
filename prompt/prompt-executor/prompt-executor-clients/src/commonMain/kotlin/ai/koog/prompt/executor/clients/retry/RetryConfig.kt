package ai.koog.prompt.executor.clients.retry

import ai.koog.http.client.KoogHttpClientException
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Configuration for retry behavior in LLM client operations.
 *
 * @property maxAttempts Maximum number of attempts (including initial)
 * @property initialDelay Initial delay before first retry
 * @property maxDelay Maximum delay between retries
 * @property backoffMultiplier Multiplier for exponential backoff
 * @property jitterFactor Random jitter factor (0.0 to 1.0)
 * @property retryablePatterns Patterns to identify retryable errors
 * @property retryAfterExtractor Optional extractor for retry-after hints. Defaults to
 *   [DEFAULT_RETRY_AFTER_EXTRACTOR], which prefers HTTP response headers and falls back to
 *   parsing the error message when no usable header is present.
 */
public data class RetryConfig @JvmOverloads constructor(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = 1.seconds,
    val maxDelay: Duration = 30.seconds,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1,
    val retryablePatterns: List<RetryablePattern> = DEFAULT_PATTERNS,
    val retryAfterExtractor: RetryAfterExtractor? = DEFAULT_RETRY_AFTER_EXTRACTOR
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be at least 1.0" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be between 0.0 and 1.0" }
        require(initialDelay <= maxDelay) {
            "initialDelay ($initialDelay) must not be greater than maxDelay ($maxDelay)"
        }
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
        @JvmField
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
         * Default retry-after extractor: prefers structured HTTP response headers
         * ([StandardHeaderRetryAfterExtractor]) and falls back to parsing the error message
         * ([DefaultRetryAfterExtractor]) when no usable header is present.
         */
        @JvmField
        public val DEFAULT_RETRY_AFTER_EXTRACTOR: RetryAfterExtractor = CompositeRetryAfterExtractor(
            StandardHeaderRetryAfterExtractor.DEFAULT,
            DefaultRetryAfterExtractor
        )

        /**
         * Conservative configuration - fewer retries, longer delays.
         */
        @JvmField
        public val CONSERVATIVE: RetryConfig = RetryConfig(
            maxAttempts = 3,
            initialDelay = 2.seconds,
            maxDelay = 30.seconds
        )

        /**
         * Aggressive configuration - more retries, shorter delays.
         */
        @JvmField
        public val AGGRESSIVE: RetryConfig = RetryConfig(
            maxAttempts = 5,
            initialDelay = 500.milliseconds,
            maxDelay = 20.seconds,
            backoffMultiplier = 1.5
        )

        /**
         * Production configuration - balanced for production use.
         */
        @JvmField
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
        @JvmField
        public val DISABLED: RetryConfig = RetryConfig(maxAttempts = 1)

        /**
         * The default retry configuration used by clients implementing retry logic.
         *
         * Suitable for general-purpose use cases where standard retry behavior is required.
         */
        @JvmField
        public val DEFAULT: RetryConfig = RetryConfig()
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
 * Extracts retry-after hints from transient errors.
 *
 * Implementations can inspect either the error message (for backward compatibility with
 * lambda-style callers) or a [KoogHttpClientException] carrying structured HTTP response
 * metadata. The default [extract] overload bridges the two: implementations that only parse
 * messages remain callable on [KoogHttpClientException] without changes.
 */
public fun interface RetryAfterExtractor {
    /**
     * Extracts a retry-after duration from the provided error message.
     *
     * @param message The error message from which to extract the retry-after duration.
     * @return The extracted retry-after duration, or null if no valid duration could be determined.
     */
    public fun extract(message: String): Duration?

    /**
     * Extracts a retry-after duration from a [KoogHttpClientException], which exposes
     * HTTP response headers, the status code, and the error body.
     *
     * The default implementation delegates to [extract] with the exception's message so that
     * existing SAM lambdas continue to work. Header-aware extractors should override this.
     *
     * @param error The HTTP client exception carrying the response metadata.
     * @return The extracted retry-after duration, or null if no valid hint could be determined.
     */
    public fun extract(error: KoogHttpClientException): Duration? =
        extract(error.message ?: "")
}

/**
 * Default implementation that extracts common retry-after patterns from free-form error text.
 */
public object DefaultRetryAfterExtractor : RetryAfterExtractor {
    private val patterns = listOf(
        Regex("retry\\s+after\\s+(\\d+)\\s+second", RegexOption.IGNORE_CASE),
        Regex("retry-after:\\s*(\\d+)", RegexOption.IGNORE_CASE),
        Regex("wait\\s+(\\d+)\\s+second", RegexOption.IGNORE_CASE),
        Regex("try again in\\s+(\\d+)(\\.\\d{1,3})?s", RegexOption.IGNORE_CASE)
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

/**
 * Extractor that reads structured rate-limit hints from HTTP response headers attached to a
 * [KoogHttpClientException].
 *
 * Recognized headers (keys on [KoogHttpClientException.headers] are already normalized to
 * lowercase; configured names are lowercased on construction):
 *
 * - [retryAfterHeaders] (default `retry-after`) - integer delta-seconds or an IMF-fixdate
 *   (RFC 9110 §10.2.3). Non-integer, non-date values are ignored.
 * - [resetDurationHeaders] (default `x-ratelimit-reset-requests`, `x-ratelimit-reset-tokens`) -
 *   Go-style durations such as `1s`, `6m0s`, or `100ms`, the format OpenAI uses (RFC 9110 does
 *   not standardize these).
 *
 * `retry-after` is authoritative (RFC 9110), so any usable value from [retryAfterHeaders] wins
 * outright; only when none is present are [resetDurationHeaders] consulted, taking the smallest
 * strictly-positive value so the client retries as soon as the first bucket refills. Every value
 * of a repeated header is considered, not just the first. Non-positive, non-finite, and
 * unparseable values count as "no hint", so the caller can fall back to exponential backoff or
 * a secondary extractor (see [CompositeRetryAfterExtractor]).
 *
 * This extractor only consults headers; [extract] with a bare message always returns `null`.
 * Compose it with [DefaultRetryAfterExtractor] to retain message-based fallback behavior.
 *
 * @property clock Clock used to evaluate HTTP-date values relative to "now". Override in tests.
 * @param retryAfterHeaders Names of headers carrying an authoritative delta-seconds or HTTP-date
 *   delay, consulted first. Defaults to [DEFAULT_RETRY_AFTER_HEADERS].
 * @param resetDurationHeaders Names of headers carrying informational Go-style reset durations,
 *   consulted only when no retry-after hint is usable. Defaults to
 *   [DEFAULT_RESET_DURATION_HEADERS].
 */
public class StandardHeaderRetryAfterExtractor @JvmOverloads constructor(
    private val clock: KoogClock = KoogClock.System,
    retryAfterHeaders: List<String> = DEFAULT_RETRY_AFTER_HEADERS,
    resetDurationHeaders: List<String> = DEFAULT_RESET_DURATION_HEADERS,
) : RetryAfterExtractor {

    private val retryAfterHeaders: List<String> = retryAfterHeaders.map { it.lowercase() }
    private val resetDurationHeaders: List<String> = resetDurationHeaders.map { it.lowercase() }

    override fun extract(message: String): Duration? = null

    override fun extract(error: KoogHttpClientException): Duration? {
        val headers = error.headers
        if (headers.isEmpty()) return null

        // retry-after is authoritative per RFC 9110, so it is honored outright; the reset
        // durations are informational and only consulted when no retry-after value is usable.
        retryAfterHeaders
            .flatMap { name -> headers.usableHints(name, ::parseRetryAfter) }
            .minOrNull()
            ?.let { return it }

        return resetDurationHeaders
            .flatMap { name -> headers.usableHints(name, ::parseResetDuration) }
            .minOrNull()
    }

    // Considers every value of a (possibly repeated or case-merged) header, keeping only usable
    // hints: parseable, strictly positive, and finite. Zero, negative, expired-date, and
    // saturated-to-infinite values are all "no hint" rather than "retry immediately/never".
    private fun Map<String, List<String>>.usableHints(
        name: String,
        parse: (String) -> Duration?
    ): List<Duration> = this[name].orEmpty().mapNotNull { value ->
        parse(value.trim())?.takeIf { it > Duration.ZERO && it.isFinite() }
    }

    private fun parseRetryAfter(value: String): Duration? {
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { return it.seconds }
        parseHttpDate(value)?.let { return it - clock.now() }
        // A proxy may fold repeated delta-seconds into one comma-separated value. An IMF-fixdate
        // also contains a comma but was matched whole above, so splitting cannot corrupt it.
        if ("," in value) {
            return value.split(',')
                .mapNotNull { part -> part.trim().toLongOrNull()?.seconds?.takeIf { it > Duration.ZERO } }
                .minOrNull()
        }
        return null
    }

    /**
     * Parses an IMF-fixdate (`"Sun, 06 Nov 1994 08:49:37 GMT"`) as an [Instant]. RFC 9110 §5.6.7
     * requires new clients to generate this form; obsolete RFC 850 and asctime formats must be
     * accepted but are rare in practice and intentionally unsupported here.
     */
    private fun parseHttpDate(value: String): Instant? {
        val match = IMF_FIXDATE.matchEntire(value) ?: return null
        val day = match.groupValues[2].padStart(2, '0')
        val month = MONTH_NUMBERS[match.groupValues[3]] ?: return null
        val year = match.groupValues[4]
        val time = "${match.groupValues[5]}:${match.groupValues[6]}:${match.groupValues[7]}"
        return runCatching { Instant.parse("$year-$month-${day}T${time}Z") }.getOrNull()
    }

    /**
     * Parses a Go-style duration (`1s`, `6m0s`, `100ms`, `7.66s`) the way OpenAI formats its
     * reset headers. [Duration.parseOrNull] accepts exactly this grammar (plus harmless
     * supersets such as `1d`); non-finite results like `Infinity` are discarded by the
     * caller's finiteness guard.
     */
    private fun parseResetDuration(value: String): Duration? = Duration.parseOrNull(value)

    /**
     * Companion exposing the default header names and a shared default instance backed by
     * [KoogClock.System].
     */
    public companion object {
        // Note: DEFAULT must stay declared below the header-name lists; companion properties
        // initialize top to bottom and its constructor defaults read both lists.

        /** Default names of authoritative delay headers: the standard `retry-after` (RFC 9110). */
        @JvmField
        public val DEFAULT_RETRY_AFTER_HEADERS: List<String> = listOf("retry-after")

        /** Default names of informational reset-duration headers (OpenAI's rate-limit buckets). */
        @JvmField
        public val DEFAULT_RESET_DURATION_HEADERS: List<String> = listOf(
            "x-ratelimit-reset-requests",
            "x-ratelimit-reset-tokens",
        )

        /** Shared instance using the system clock and the default header names. */
        @JvmField
        public val DEFAULT: StandardHeaderRetryAfterExtractor = StandardHeaderRetryAfterExtractor()

        private val IMF_FIXDATE = Regex(
            "(Mon|Tue|Wed|Thu|Fri|Sat|Sun), " +
                "(\\d{2}) " +
                "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) " +
                "(\\d{4}) " +
                "(\\d{2}):(\\d{2}):(\\d{2}) GMT"
        )

        private val MONTH_NUMBERS = mapOf(
            "Jan" to "01", "Feb" to "02", "Mar" to "03", "Apr" to "04",
            "May" to "05", "Jun" to "06", "Jul" to "07", "Aug" to "08",
            "Sep" to "09", "Oct" to "10", "Nov" to "11", "Dec" to "12"
        )
    }
}

/**
 * Runs a list of [RetryAfterExtractor]s in order and returns the first non-null result. Useful
 * for stacking a header-aware extractor over a message-based fallback: the ordering models
 * the idea that structured hints are preferred when present.
 *
 * @param extractors Extractors to consult, in order.
 */
public class CompositeRetryAfterExtractor(
    private val extractors: List<RetryAfterExtractor>
) : RetryAfterExtractor {
    /** Convenience varargs constructor. */
    public constructor(vararg extractors: RetryAfterExtractor) : this(extractors.toList())

    override fun extract(message: String): Duration? =
        extractors.firstNotNullOfOrNull { it.extract(message) }

    override fun extract(error: KoogHttpClientException): Duration? =
        extractors.firstNotNullOfOrNull { it.extract(error) }

    /** Renders the chain so retry logs make the consultation order obvious. */
    override fun toString(): String =
        extractors.joinToString(prefix = "CompositeRetryAfterExtractor(", postfix = ")")
}
