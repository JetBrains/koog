# Creating prompts

Koog provides a structured way to create prompts with control over message types, their order, and content:

- For **Kotlin** users, through a type-safe Kotlin DSL.
- For **Java** users, through a fluent builder API.

## Basic structure

The `prompt()` function in Kotlin or the `Prompt.builder()` in Java create a Prompt object with a unique ID and a list of messages:

```
val prompt = prompt("unique_prompt_id") {
    // List of messages
}
```

```
Prompt prompt = Prompt.builder("unique_prompt_id")
    // List of messages
    .build();
```

## Message types

The Kotlin DSL and the Java builder API support the following types of messages, each of which corresponds to a specific role in a conversation:

- **System message**: Provides the context, instructions, and constraints to the LLM, defining its behavior.
- **User message**: Represents the user input.
- **Assistant message**: Represents LLM responses that are used for few-shot learning or to continue the conversation.
- **Tool message**: Represents tool calls and their results.

```
val prompt = prompt("unique_prompt_id") {
    // Add a system message to set the context
    system("You are a helpful assistant with access to tools.")
    // Add a user message
    user("What is 5 + 3 ?")
    // Add an assistant message
    assistant("The result is 8.")
}
```

```
Prompt prompt = Prompt.builder("unique_prompt_id")
    // Add a system message to set the context
    .system("You are a helpful assistant with access to tools.")
    // Add a user message
    .user("What is 5 + 3 ?")
    // Add an assistant message
    .assistant("The result is 8.")
    .build();
```

### System message

A system message defines the LLM behavior and sets the context for the entire conversation. It can specify the model's role, tone, provide guidelines and constraints on responses, and provide response examples.

To create the system message, provide a string as an argument to the `system()` Kotlin function or Java method:

```
val prompt = prompt("system_message") {
    system("You are a helpful assistant that explains technical concepts.")
}
```

```
Prompt prompt = Prompt.builder("system_message")
    .system("You are a helpful assistant that explains technical concepts.")
    .build();
```

### User messages

A user message represents input from the user. To create the user message, provide a string as an argument to the `user()` Kotlin function or Java method:

```
val prompt = prompt("user_message") {
    system("You are a helpful assistant.")
    user("What is Koog?")
}
```

```
Prompt prompt = Prompt.builder("user_message")
    .system("You are a helpful assistant.")
    .user("What is Koog?")
    .build();
```

Most user messages contain plain text, but they can also include multimodal content, such as images, audio, video, and documents. For details and examples, see [Multimodal content](multimodal-content/).

### Assistant messages

An assistant message represents an LLM response, which can be used for few-shot learning in future similar interactions, to continue a conversation, or to demonstrate the expected output structure.

To create the assistant message, provide a string as an argument to the `assistant()` Kotlin function or Java method:

```
val prompt = prompt("article_review") {
    system("Evaluate the article.")

    // Example 1
    user("The article is clear and easy to understand.")
    assistant("positive")

    // Example 2
    user("The article is hard to read but it's clear and useful.")
    assistant("neutral")

    // Example 3
    user("The article is confusing and misleading.")
    assistant("negative")

    // New input to classify
    user("The article is interesting and helpful.")
}
```

```
Prompt prompt = Prompt.builder("article_review")
    .system("Evaluate the article.")

    // Example 1
    .user("The article is clear and easy to understand.")
    .assistant("positive")

    // Example 2
    .user("The article is hard to read but it's clear and useful.")
    .assistant("neutral")

    // Example 3
    .user("The article is confusing and misleading.")
    .assistant("negative")

    // New input to classify
    .user("The article is interesting and helpful.")
    .build();
```

### Tool messages

A tool message represents a tool call and its result, which can be used to pre-fill the history of tool calls.

Tip

An LLM generates tool calls during execution. Pre-filling them is helpful for few-shot learning or demonstrating how the tools are expected to be used.

To create the tool message, call the `tool()` function in Kotlin or the `toolCall()` and `toolResult()` methods in Java:

```
val prompt = prompt("calculator_example") {
    system("You are a helpful assistant with access to tools.")
    user("What is 5 + 3?")
    // Tool call
    toolCall(
        id = "calculator_tool_id",
        tool = "calculator",
        args = """{"operation": "add", "a": 5, "b": 3}"""
    )
    // Tool result
    toolResult(
        id = "calculator_tool_id",
        tool = "calculator",
        output = "8"
    )

    // LLM response based on tool result
    assistant("The result of 5 + 3 is 8.")
    user("What is 4 + 5?")
}
```

```
Prompt prompt = Prompt.builder("calculator_example")
    .system("You are a helpful assistant with access to tools.")
    .user("What is 5 + 3?")
    // Tool call
    .toolCall("calculator_tool_id", "calculator", "{\"operation\": \"add\", \"a\": 5, \"b\": 3}")
    // Tool result
    .toolResult("calculator_tool_id", "calculator", "8")
    // LLM response based on tool result    
    .assistant("The result of 5 + 3 is 8.")
    .user("What is 4 + 5?")
    .build();
```

## Text message builders

Warning

Text message builders are available only in Kotlin.

When building a `system()`, `user()`, or `assistant()` message, you can use helper [text-building-functions](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.text/-text-content-builder/index.html) for rich text formatting.

```
val prompt = prompt("text_example") {
    user {
        +"Review the following code snippet:"
        +"fun greet(name: String) = println(\"Hello, \$name!\")"

        // Paragraph break
        br()
        text("Please include in your explanation:")

        // Indent content
        padding("  ") {
            +"1. What the function does."
            +"2. How string interpolation works."
        }
    }
}
```

You can also use the [Markdown](https://api.koog.ai/prompt/prompt-markdown/ai.koog.prompt.markdown/markdown.html) and [XML](https://api.koog.ai/prompt/prompt-xml/ai.koog.prompt.xml/xml.html) builders to add the content in the corresponding format.

```
val prompt = prompt("markdown_xml_example") {
    // A user message in Markdown format
    user {
        markdown {
            h2("Evaluate the article using the following criteria:")
            bulleted {
                item { +"Clarity and readability" }
                item { +"Accuracy of information" }
                item { +"Usefulness to the reader" }
            }
        }
    }
    // An assistant message in XML format
    assistant {
        xml {
            xmlDeclaration()
            tag("review") {
                tag("clarity") { text("positive") }
                tag("accuracy") { text("neutral") }
                tag("usefulness") { text("positive") }
            }
        }
    }
}
```

Tip

You can mix the text building functions with the XML and Markdown builders.

## Prompt parameters

Prompts can be customized by configuring parameters that control the LLM's behavior.

```
val prompt = prompt(
    id = "custom_params",
    params = LLMParams(
        temperature = 0.7,
        numberOfChoices = 1,
        toolChoice = LLMParams.ToolChoice.Auto
    )
) {
    system("You are a creative writing assistant.")
    user("Write a song about winter.")
}
```

```
// Create params first
LLMParams params = new LLMParams(
    0.7,                    // temperature
    null,                   // maxTokens
    1,                      // numberOfChoices
    null,                   // speculation
    null,                   // schema
    LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
    null,                   // user
    null                    // additionalProperties
);

Prompt prompt = Prompt.builder("custom_params")
    .system("You are a creative writing assistant.")
    .user("Write a song about winter.")
    .build();

// Apply params to the built prompt
prompt = prompt.withParams(params);
```

The following parameters are supported:

- `temperature`: Controls randomness in the model's responses.
- `toolChoice`: Controls tool calling behavior of the model.
- `numberOfChoices`: Requests multiple alternative responses.
- `schema`: Defines the structure for the model's response format.
- `maxTokens`: Limits the number of tokens in the response.
- `speculation`: Provides a hint about the expected response format (only supported by specific models).

For more information, see [LLM parameters](../../llm-parameters/).

## Extending existing prompts

You can extend an existing prompt by calling the `prompt()` function in Kotlin or the `Prompt.builder()` in Java with the existing prompt as an argument:

```
val basePrompt = prompt("base") {
    system("You are a helpful assistant.")
    user("Hello!")
    assistant("Hi! How can I help you?")
}

val extendedPrompt = prompt(basePrompt) {
    user("What's the weather like?")
}
```

```
Prompt basePrompt = Prompt.builder("base")
    .system("You are a helpful assistant.")
    .user("Hello!")
    .assistant("Hi! How can I help you?")
    .build();

Prompt extendedPrompt = Prompt.builder(String.valueOf(basePrompt))
    .user("What's the weather like?")
    .build();
```

This creates a new prompt that includes all messages from `basePrompt` and the new user message.

## Next steps

- Learn how to work with [multimodal content](multimodal-content/).
- Run prompts with [LLM clients](../llm-clients/) if you work with a single LLM provider.
- Run prompts with [prompt executors](../prompt-executors/) if you work with multiple LLM providers.
- Learn how to use llm cache with [cache control](cache-control/).
