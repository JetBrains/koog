# Structured prompts

Koog uses the type-safe Kotlin DSL to create structured prompts with control over message types,
their order, and content.

The structured prompts let you pre-configure conversation history with multiple messages, provide multimodal content, 
examples, tool calls, and their results.

## Basic structure

The `prompt()` function creates a Prompt object with a unique ID and a list of messages:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
-->
```kotlin
val prompt = prompt("unique_prompt_id") {
    // List of messages
}
```
<!--- KNIT example-structured-prompts-01.kt -->

## Message types

The Kotlin DSL supports the following types of messages, each of which corresponds to a specific role in a conversation:

- **System message**: Provides the context, instructions, and constraints to the LLM, defining its behavior.
- **User message**: Represents the user input that can contain text, images, audio, video, or documents.
- **Assistant message**: Represents LLM responses that are used for few-shot learning or to continue the conversation.
- **Tool message**: Represents tool calls and their results.

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
-->
```kotlin
val prompt = prompt("unique_prompt_id") {
    // Add a system message to set the context
    system("You are a helpful assistant with access to tools.")
    // Add a user message
    user("What is 5 + 3 ?")
    // Add an assistant message
    assistant("The result is 8.")
}
```
<!--- KNIT example-structured-prompts-02.kt -->

### System message

A system message defines the LLM behavior and sets the context for the entire conversation.
It can specify the model's role, tone, provide guidelines and constraints on responses, and provide response examples.

To create the system message, provide a string to the `system()` function as an argument:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
-->
```kotlin
val prompt = prompt("assistant") {
    system("You are a helpful assistant that explains technical concepts.")
}
```
<!--- KNIT example-structured-prompts-03.kt -->

### User messages

A user message represents input from the user.
It can include plain text or multimodal content (such as images, audio, video, and documents).

To create the user message, provide a string to the `user()` function as an argument:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
-->
```kotlin
val prompt = prompt("question") {
    system("You are a helpful assistant.")
    user("What is Koog?")
}
```
<!--- KNIT example-structured-prompts-04.kt -->

For details about multimodal content, see [Multimodal inputs](#multimodal-inputs).

### Assistant messages

An assistant message represents an LLM response, which can be used for few-shot learning in future similar interactions,
to continue a conversation, or to demonstrate the expected output structure.

To create the assistant message, provide a string to the `assistant()` function as an argument:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
-->
```kotlin
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
<!--- KNIT example-structured-prompts-05.kt -->

### Tool messages

A tool message represents a tool call and its result, which can be used to pre-fill the history of tool calls.

!!! tip
    An LLM generates tool calls during execution.
    Pre-filling them is helpful for few-shot learning or demonstrating how the tools are expected to be used.

To create the tool message, call the `tool()` function:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
-->
```kotlin
val prompt = prompt("calculator_example") {
    system("You are a helpful assistant with access to tools.")
    user("What is 5 + 3?")
    tool {
        // Tool call
        call(
            id = "calculator_tool_id",
            tool = "calculator",
            content = """{"operation": "add", "a": 5, "b": 3}"""
        )

        // Tool result
        result(
            id = "calculator_tool_id",
            tool = "calculator",
            content = "8"
        )
    }

    // LLM response based on tool result
    assistant("The result of 5 + 3 is 8.")
}
```
<!--- KNIT example-structured-prompts-06.kt -->

## Text message builders

When building a `system()`, `user()`, or `assistant()` message, you can use 
helper [text-building functions](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.text/-text-content-builder/index.html)
for rich text formatting.

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
-->
```kotlin
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
<!--- KNIT example-structured-prompts-07.kt -->

You can also use the [Markdown](https://api.koog.ai/prompt/prompt-markdown/ai.koog.prompt.markdown/markdown.html) 
and [XML](https://api.koog.ai/prompt/prompt-xml/ai.koog.prompt.xml/xml.html) builders to add the content in 
the corresponding format.

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.xml.xml
-->
```kotlin
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
<!--- KNIT example-structured-prompts-08.kt -->

!!! tip
    You can mix the text building functions with the XML and Markdown builders.

## Prompt parameters

Prompts can be customized by configuring parameters that control the LLM's behavior.

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
-->
```kotlin
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
<!--- KNIT example-structured-prompts-09.kt -->

The following parameters are supported:

- `temperature`: Controls randomness (0.0 = focused/deterministic, 1.0+ = creative/diverse)
- `toolChoice`: Tool usage strategy (`Auto`, `Required`, `Named(toolName)`)
- `numberOfChoices`: Request multiple independent responses
- `schema`: Define structured output format (for structured outputs)

For more information, see [LLM parameters](llm-parameters.md).

## Extending existing prompts

You can extend an existing prompt by calling the `prompt()` function with the existing prompt as an argument:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
-->
```kotlin
val basePrompt = prompt("base") {
    system("You are a helpful assistant.")
    user("Hello!")
    assistant("Hi! How can I help you?")
}

val extendedPrompt = prompt(basePrompt) {
    user("What's the weather like?")
}
```
<!--- KNIT example-structured-prompts-10.kt -->

This creates a new prompt that includes all messages from `basePrompt` and the new user message.

## Next steps

- Learn how to work with [multimodal content](multimodal-inputs.md).
- Run prompts with [LLM clients](llm-clients.md) if you work with a single LLM provider.
- Run prompts with [prompt executors](prompt-executors.md) if you work with multiple LLM providers.
