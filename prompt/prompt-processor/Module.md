# Module prompt-processor

A module for processing and fixing LLM responses.

### Overview

The prompt-processor module provides utilities for post-processing responses from language models. Its primary focus is
fixing incorrectly formatted tool calls that may occur when LLMs generate responses. The module includes both JSON-based
fixes for common formatting issues and LLM-based approaches for more complex corrections.

Key components:

- **ResponseProcessor**: An abstract base class for implementing response processors, with support for chaining multiple
  processors together.
- **ManualToolCallFixProcessor**: A processor that fixes invalid tool call JSONs, handling incorrect keys and missing escapes.
- **LLMBasedToolCallFixProcessor**: An advanced processor that uses the LLM itself to iteratively fix incorrectly generated tool
  calls.

### Example of usage

Basic usage with `ManualToolCallFixProcessor`

```kotlin
val processor = ManualToolCallFixProcessor(toolRegistry)

// Execute a prompt with response processing
val responses = executor.executeProcessed(prompt, model, tools, processor)
```

Customizing JSON key mappings for different LLM providers

```kotlin
val customConfig = ToolCallJsonConfig(
    idJsonKeys = ToolCallJsonConfig.defaultIdJsonKeys + listOf("custom_id"),
    nameJsonKeys = ToolCallJsonConfig.defaultNameJsonKeys + listOf("function_name"),
    argsJsonKeys = ToolCallJsonConfig.defaultArgsJsonKeys + listOf("function_args")
)

val processor = ManualToolCallFixProcessor(toolRegistry, customConfig)
```

Chaining multiple processors

```kotlin
val processor1 = ManualToolCallFixProcessor(toolRegistry)
val processor2 = LLMBasedToolCallFixProcessor(toolRegistry)

val chainedProcessor = processor1 + processor2
val responses = executor.executeProcessed(prompt, model, tools, chainedProcessor)
```

Using processor with an agent

```kotlin
val agent = AIAgent(
    executor = yourExecutor,
    model = yourModel,
    responseProcessor = yourProcessor,
)
```
