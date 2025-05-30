package ai.koog.prompt.tokenizer

/**
 * Interface for tokenizing text and counting tokens.
 * 
 * Tokenizers are used to estimate the number of tokens in a text string.
 * This is particularly useful for LLMs that don't provide token counts
 * in their responses, requiring client-side estimation, or if you want to have
 * more fine-grained tokens estimation for each request message in the prompt.
 *
 * Different implementations can provide varying levels of accuracy and performance.
 * You can implement your own tokenizer and easily integrate it with Koog.
 */
public interface Tokenizer {
    /**
     * Counts the number of tokens in the given text.
     *
     * @param text The text to tokenize and count
     * @return The estimated number of tokens in the text
     */
    public fun countTokens(text: String): Int
}

/**
 * A tokenizer implementation that always returns 0.
 * 
 * This is useful when token counting is not needed or when you want to
 * save computational resources by skipping token counting.
 */
public class NoTokenizer : Tokenizer {
    /**
     * Always returns 0, regardless of the input text.
     *
     * @param text The text to tokenize (ignored)
     * @return Always returns 0
     */
    override fun countTokens(text: String): Int = 0
}

/**
 * A simple regex-based tokenizer that splits text on whitespace and common punctuation.
 * 
 * This tokenizer provides a reasonable approximation of token counts for most LLMs,
 * though it's not as accurate as model-specific tokenizers. It's efficient and doesn't
 * require any external dependencies.
 * 
 * Note: Ollama does not provide tokens in responses, so this client-side estimation
 * is necessary for token counting.
 */
public class SimpleRegexBasedTokenizer : Tokenizer {
    /**
     * Counts tokens by splitting on whitespace and common punctuation.
     * 
     * The implementation adds a small overhead factor (1.1x) to account for
     * special tokens and tokenization differences in actual LLM tokenizers.
     *
     * @param text The text to tokenize
     * @return The estimated number of tokens in the text
     */
    override fun countTokens(text: String): Int {
        // Split on whitespace and common punctuation
        val tokens = text.split(Regex("\\s+|[,.;:!?()\\[\\]{}\"']+"))
            .filter { it.isNotEmpty() }

        // Add a small overhead for special tokens and tokenization differences
        return (tokens.size * 1.1).toInt()
    }
}