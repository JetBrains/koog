# Module agents-features-tokenizer

Provides implementation of the `MessageTokenizer` feature for AI Agents

### Overview

The MessageTokenizer feature enables token counting and management for AI Agent messages and prompts, including:
- Counting tokens in individual messages
- Calculating total token usage in prompts
- Supporting both on-demand and cached tokenization strategies
- Integration with AI Agent pipelines and LLM contexts

This functionality is crucial for:
- Managing token limits when working with LLMs
- Optimizing prompt design based on token usage
- Monitoring and controlling costs associated with token usage
- Performance optimization through token caching

### Using in your project

To use the MessageTokenizer feature in your agent:

```kotlin
val agent = AIAgent(
    promptExecutor = executor,
    strategy = strategy,
    // other parameters...
) {
    install(MessageTokenizer) {
        // Configure the tokenizer implementation
        tokenizer = YourTokenizerImplementation()

        // Enable or disable caching (enabled by default)
        enableCaching = true
    }
}
```

### Accessing token information

Once installed, you can access the tokenizer through the agent context:

```kotlin
val myStrategy = strategy("token-aware-strategy") {
    val checkTokens by node<String, String> {
        // Access the tokenizer through the LLM context
        val totalTokens = llm.readSession {
            tokenizer.tokenCountFor(prompt)
        }

        // Use token information in your strategy
        "Total tokens used: $totalTokens"
    }

    // Connect to other nodes in your strategy
    edge(nodeStart forwardTo checkTokens)
    edge(checkTokens forwardTo nodeFinish)
}
```

### Example of usage

Here's an example of using the tokenizer to count tokens in a prompt:

```kotlin
// Create a prompt with some messages
val myPrompt = prompt("example-prompt") {
    system("You are a helpful assistant.")
    user("What is the capital of France?")
    assistant("Paris is the capital of France.")
}

// Count tokens in the entire prompt
val totalTokens = tokenizer.tokenCountFor(myPrompt)
println("Total tokens in prompt: $totalTokens")

// Count tokens for individual messages
val systemTokens = tokenizer.tokenCountFor(Message.System("You are a helpful assistant."))
val userTokens = tokenizer.tokenCountFor(Message.User("What is the capital of France?"))
val assistantTokens = tokenizer.tokenCountFor(Message.Assistant("Paris is the capital of France."))

println("System message tokens: $systemTokens")
println("User message tokens: $userTokens")
println("Assistant message tokens: $assistantTokens")
```
