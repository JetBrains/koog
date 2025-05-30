# Module prompt-tokenizer

A module that provides interfaces and implementations for tokenizing text and counting tokens when working with Large Language Models (LLMs).

### Overview

The prompt-tokenizer module offers tools for estimating the number of tokens in text strings, which is essential for working with LLMs that have token limits or require token counting. The module includes:

- A `Tokenizer` interface that defines the contract for token counting
- Multiple implementations with varying levels of accuracy and performance:
  - `NoTokenizer`: A minimal implementation that always returns 0 (useful when token counting is not needed)
  - `RegexBasedTokenizer`: A simple implementation that provides reasonable approximations for most LLMs
  - `TiktokenEncoder`: An advanced implementation based on OpenAI's tiktoken library, using byte pair encoding (BPE) for more accurate token counting

This module is particularly useful for LLMs that don't provide token counts in their responses, requiring client-side estimation.
Also it's allowing to provide more fine-grained token usage estimation for each single request message in the prompt.

### Example of usage

```kotlin
// Using NoTokenizer when token counting is not needed
val noTokenizer = NoTokenizer()
val count1 = noTokenizer.countTokens("This will always return 0") // Returns 0

// Using RegexBasedTokenizer for simple approximation
val regexTokenizer = RegexBasedTokenizer()
val count2 = regexTokenizer.countTokens("Hello, world!") // Returns approximate token count

// Using TiktokenEncoder for more accurate token counting
// First, create a vocabulary and pattern
val vocabulary = mapOf(
    "Hello".toByteArrayKey() to 1,
    "World".toByteArrayKey() to 2,
    "!".toByteArrayKey() to 3
)
val pattern = Regex("\\w+|[^\\w\\s]")
val unkTokenId = 0

// Then create the encoder and count tokens
val tiktokenEncoder = TiktokenEncoder(vocabulary, pattern, unkTokenId)
val count3 = tiktokenEncoder.countTokens("Hello World!") // Returns 3 tokens
```

### Custom Tokenizer Implementation

You can implement your own tokenizer by implementing the `Tokenizer` interface:

```kotlin
class MyCustomTokenizer : Tokenizer {
    override fun countTokens(text: String): Int {
        // Your custom token counting logic here
        return calculatedTokenCount
    }
}
```
