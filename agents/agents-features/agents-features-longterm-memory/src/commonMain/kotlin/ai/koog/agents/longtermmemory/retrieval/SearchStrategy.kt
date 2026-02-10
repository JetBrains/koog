package ai.koog.agents.longtermmemory.retrieval

/**
 * Search strategy for creating search requests during prompt augmentation.
 *
 * This is a functional interface (SAM) that defines how a user query string
 * should be transformed into a [SearchRequest] for storage.
 *
 * Pre-built implementations are available for common search types:
 * - [SimilaritySearchStrategy] - Vector similarity search (semantic search)
 * - [KeywordSearchStrategy] - Full-text/keyword search
 *
 * ### Usage Examples
 *
 * **Using pre-built strategies (Kotlin):**
 * ```kotlin
 * retrieval {
 *     searchStrategy = SimilaritySearchStrategy(topK = 5, similarityThreshold = 0.7)
 * }
 * ```
 *
 * **Custom implementation as lambda (Java):
 * ```java
 * SearchStrategy customStrategy = (query) ->
 *     new SimilaritySearchRequest(query, 5, 0.8, null);
 * ```
 */
public fun interface SearchStrategy {
    /**
     * Maps a query string into a [SearchRequest] for the storage.
     *
     * @param query The user's query string (typically the last user message content)
     * @return The search request to be executed
     */
    public fun create(query: String): SearchRequest

    /**
     * Companion object with a builder method.
     */
    public companion object {
        /**
         * Returns a builder that lets you choose a default [SearchStrategy] implementation.
         *
         * Example usage (Java):
         * ```java
         * SearchStrategy.builder()
         *     .similarity()
         *     .withTopK(5)
         *     .withSimilarityThreshold(0.7)
         *     .build()
         * ```
         */
        @kotlin.jvm.JvmStatic
        public fun builder(): SearchStrategyBuilder = SearchStrategyBuilder()
    }
}

/**
 * Intermediate builder that lets callers select a [SearchStrategy] implementation.
 */
public class SearchStrategyBuilder {
    /**
     * Select the [SimilaritySearchStrategy] implementation.
     * Returns its [SimilaritySearchStrategy.Builder] for further configuration.
     */
    public fun similarity(): SimilaritySearchStrategy.Builder = SimilaritySearchStrategy.Builder()

    /**
     * Select the [KeywordSearchStrategy] implementation.
     * Returns its [KeywordSearchStrategy.Builder] for further configuration.
     */
    public fun keyword(): KeywordSearchStrategy.Builder = KeywordSearchStrategy.Builder()
}

/**
 * Keyword search mode using full-text/lexical matching.
 *
 * This mode uses traditional text matching instead of vector similarity,
 * which can be useful for exact term matching or when semantic search
 * is not needed.
 *
 * @property topK Maximum number of results to return
 * @property similarityThreshold Minimum similarity score (0.0 to 1.0)
 * @property filterExpression Optional metadata filter expression for pre-filtering
 */
public class KeywordSearchStrategy(
    public val topK: Int = 10,
    public val similarityThreshold: Double = 0.0,
    public val filterExpression: String? = null
) : SearchStrategy {
    override fun create(query: String): SearchRequest =
        KeywordSearchRequest(query, topK, similarityThreshold, filterExpression)

    /**
     * Builder for [KeywordSearchStrategy].
     *
     * @see KeywordSearchStrategy
     */
    public class Builder {
        /** Maximum number of results to return. */
        public var topK: Int = 10

        /** Minimum similarity score (0.0 to 1.0). */
        public var similarityThreshold: Double = 0.0

        /** Optional metadata filter expression for pre-filtering. */
        public var filterExpression: String? = null

        /** Fluent setter for [topK]. */
        public fun withTopK(topK: Int): Builder = apply { this.topK = topK }

        /** Fluent setter for [similarityThreshold]. */
        public fun withSimilarityThreshold(similarityThreshold: Double): Builder =
            apply { this.similarityThreshold = similarityThreshold }

        /** Fluent setter for [filterExpression]. */
        public fun withFilterExpression(filterExpression: String?): Builder =
            apply { this.filterExpression = filterExpression }

        /** Builds a [KeywordSearchStrategy] from the current settings. */
        public fun build(): KeywordSearchStrategy =
            KeywordSearchStrategy(topK, similarityThreshold, filterExpression)
    }
}

/**
 * Similarity search mode using vector embeddings for semantic search.
 *
 * This mode converts the query to a vector embedding and finds records
 * with similar embeddings in the vector database.
 *
 * @property topK Maximum number of results to return
 * @property similarityThreshold Minimum similarity score (0.0 to 1.0)
 * @property filterExpression Optional metadata filter expression for pre-filtering
 */
public class SimilaritySearchStrategy(
    public val topK: Int = 10,
    public val similarityThreshold: Double = 0.0,
    public val filterExpression: String? = null
) : SearchStrategy {
    override fun create(query: String): SearchRequest =
        SimilaritySearchRequest(query, topK, similarityThreshold, filterExpression)

    /**
     * Builder for [SimilaritySearchStrategy].
     *
     * @see SimilaritySearchStrategy
     */
    public class Builder {
        /** Maximum number of results to return. */
        public var topK: Int = 10

        /** Minimum similarity score (0.0 to 1.0). */
        public var similarityThreshold: Double = 0.0

        /** Optional metadata filter expression for pre-filtering. */
        public var filterExpression: String? = null

        /** Fluent setter for [topK]. */
        public fun withTopK(topK: Int): Builder = apply { this.topK = topK }

        /** Fluent setter for [similarityThreshold]. */
        public fun withSimilarityThreshold(similarityThreshold: Double): Builder =
            apply { this.similarityThreshold = similarityThreshold }

        /** Fluent setter for [filterExpression]. */
        public fun withFilterExpression(filterExpression: String?): Builder =
            apply { this.filterExpression = filterExpression }

        /** Builds a [SimilaritySearchStrategy] from the current settings. */
        public fun build(): SimilaritySearchStrategy =
            SimilaritySearchStrategy(topK, similarityThreshold, filterExpression)
    }
}
