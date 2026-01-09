# Basic agents

A basic agent uses a predefined strategy with a simple execution flow that works for most common use cases.
It accepts a string input (a question, request, or task description) and sends this input to the configured LLM.
The agent may decide to use one of the provided tools.
In this case, it sends the tool results back to the LLM,
and this repeats until the LLM does not request any more tool calls.
The agent then outputs a string response.

In [graph-based agents](graph-based-agents.md),
you can see how to re-create the predefined strategy graph used by basic agents.

??? note "Prerequisites"

    --8<-- "getting-started-snippets.md:prerequisites"

    --8<-- "getting-started-snippets.md:dependencies"

    --8<-- "getting-started-snippets.md:api-key"

    Examples on this page assume that you have set the `OPENAI_API_KEY` environment variable.

## Create a minimal agent

The [`AIAgent`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent/-a-i-agent/index.html) interface
is the primary starting point for creating Koog agents.
The overloaded `invoke()` operator functions on its companion object
enable you to instantiate this interface with a constructor-like syntax.

To create the most basic agent, provide a [prompt executor](../prompts/prompt-executors.md)
and a [language model](../model-capabilities.md#creating-a-model-llmodel-configuration):

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4o
)
```
<!--- KNIT example-basic-01.kt -->

This agent will expect a string as input and return a string as output.
To run the agent, use the `run()` function with some user input:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4o
)
-->
```kotlin
fun main() = runBlocking {
    val result = agent.run("Hello! How can you help me?")
    println(result)
}
```
<!--- KNIT example-basic-02.kt -->

The agent will return a generic answer, such as:

```text
I can assist with a wide range of topics and tasks. Here are some examples:

1. **Answering questions**: I can provide information on various subjects, from science and history to entertainment and culture.
2. **Generating text**: I can help with writing tasks, such as suggesting alternative phrases, providing definitions, or even creating entire articles or stories.
3. **Translation**: I can translate text from one language to another, including popular languages such as Spanish, French, German, Chinese, and many more.
4. **Conversation**: I can engage in natural-sounding conversations, using context and understanding to respond to questions and statements.
5. **Brainstorming**: I can help generate ideas for creative projects, such as writing stories, composing music, or coming up with business ideas.
6. **Learning**: I can help with language learning, explaining grammar rules, vocabulary, and pronunciation.
7. **Calculations**: I can perform mathematical calculations, including basic arithmetic, algebra, and more advanced math concepts.

What's on your mind? Do you have a specific question, topic, or task you'd like to tackle?
```

## Add a system prompt

Provide a [system message](../prompts/prompt-creation/index.md#system-message) to define the agent's role
as well as the purpose, context, and instructions related to the task.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are an expert in internet memes. Be helpful, friendly, and answer user questions concisely, showing your knowledge of memes.",
    llmModel = OpenAIModels.Chat.GPT4o
)
```
<!--- KNIT example-basic-03.kt -->

The instructions in the system prompt will guide the agent's response:

```text
I'm here to help you navigate the wild world of internet memes!

What's on your mind? Are you trying to understand a specific meme, need help finding a popular joke, or perhaps want some recommendations for trending memes? Let me know, and I'll do my best to provide you with some LOLs!
```

## Configure LLM output

You can provide some [LLM parameters](../llm-parameters.md#llm-parameter-reference) directly to the agent constructor
to customize the behavior of the LLM.
For example, use the `temperature` parameter to adjust the randomness of the generated responses:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are an expert in internet memes. Be helpful, friendly, and answer user questions concisely, showing your knowledge of memes.",
    llmModel = OpenAIModels.Chat.GPT4o,
    temperature = 0.7
)
```
<!--- KNIT example-basic-04.kt -->

Here are some response examples with different temperature values:

=== "0.4"
    
    ```text
    I'm here to help you navigate the wild world of internet memes! Whether you're looking for explanations, examples, or just want to share a meme with someone, I'm your go-to expert. What's on your mind? Got a specific meme in mind that's got you curious? Or maybe you need some meme-related advice? Fire away!
    ```

=== "0.7"

    ```text
    I'm here to help you navigate the wild world of internet memes!
    
    What's on your mind? Need help understanding a specific meme, finding a popular joke or trend, or maybe even creating your own meme? Let's get this meme party started!
    ```

=== "1.0"

    ```text
    I'd be happy to help you navigate the wild world of internet memes!
    
    Whether you're looking for explanations of classic memes, suggestions for new ones to try out, or just want to discuss your favorite meme culture trends, I'm here to assist. What's on your mind?
    
    Do you have a specific question about memes (e.g., "What does this meme mean?"), or are you looking for some meme-related recommendations (e.g., "Can you recommend a funny meme to share with friends?"). Let me know how I can help!
    ```
    
## Add tools

Agents can use [tools](../tools-overview.md) to perform specific tasks.
Koog provides some built-in tools, or you can implement your own custom tools.

To configure tools, use the `toolRegistry` parameter that defines the tools available to the agent:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are an expert in internet memes. Be helpful, friendly, and answer user questions concisely, showing your knowledge of memes.",
    llmModel = OpenAIModels.Chat.GPT4o,
    temperature = 0.7,
    toolRegistry = ToolRegistry {
        tool(AskUser)
    }
)
```
<!--- KNIT example-basic-05.kt -->

In the example, [`AskUser`](https://api.koog.ai/agents/agents-ext/ai.koog.agents.ext.tool/-ask-user/index.html)
is a built-in tool that helps the agent maintain a conversation with the user.
If the agent decides to ask the user a question,
it can call this tool that writes to `stdout` via `println()` and reads from `stdin` via `readln()`.

Here is an example interaction with the agent:

```text
Agent: Which meme would you like me to explain? Please choose from: Grumpy Cat, Success Kid, or Doge.

User: Explain Doge

Agent:

**Doge**

Doge is a popular internet meme that originated on the imageboard website 4chan in 2013. The meme features a picture of a Shiba Inu dog with a caption written in a playful, exaggerated style.

The typical format of a Doge meme includes:

* A photo of a Shiba Inu dog
* A caption written in all capital letters, using a intentionally simplistic and childlike tone
* Exaggerated or made-up words or phrases, often used to convey a humorous or nonsensical idea

Examples of Doge memes might include:

* "Such wow. Such happy."
* "I had fun today!"
* "Wow, I am good at napping."

The meme is known for its lighthearted and playful tone, and is often used to express excitement, happiness, or silliness. The meme has since become a cultural phenomenon, with countless variations and parodies emerging online.
```

## Adjust agent iterations

To avoid infinite loops, Koog allows any agent to take a limited number of steps (50 by default).
Use the `maxIterations` parameter to either increase this limit if you expect the agent to require more steps
(such as tool calls and LLM requests) or decrease it for agents that require only a few steps.
For example, a simple agent described here is not likely to require more than 10 steps:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are an expert in internet memes. Be helpful, friendly, and answer user questions concisely, showing your knowledge of memes.",
    llmModel = OpenAIModels.Chat.GPT4o,
    temperature = 0.7,
    toolRegistry = ToolRegistry {
        tool(AskUser)
    },
    maxIterations = 10
)
```
<!--- KNIT example-basic-06.kt -->

!!! tip

    Instead of passing the model, temperature, max iterations,
    and other parameters directly to the agent constructor,
    you can also define and pass them as a separate configuration object.
    For more information, see [Agent configuration](index.md#agent-configuration).

## Handle events during agent runtime

To assist with testing and debugging, as well as making hooks for chained agent interactions,
Koog provides the [EventHandler](https://api.koog.ai/agents/agents-features/agents-features-event-handler/ai.koog.agents.features.eventHandler.feature/-event-handler/index.html) feature.
Call the `handleEvents()` function inside the agent constructor lambda to install the feature and register event handlers:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are an expert in internet memes. Be helpful, friendly, and answer user questions concisely, showing your knowledge of memes.",
    llmModel = OpenAIModels.Chat.GPT4o,
    temperature = 0.7,
    toolRegistry = ToolRegistry {
        tool(AskUser)
    },
    maxIterations = 10
){
    handleEvents {
        // Handle tool calls
        onToolCallStarting { eventContext ->
            println("Tool called: ${eventContext.toolName} with args ${eventContext.toolArgs}")
        }
    }
}
```
<!--- KNIT example-basic-07.kt -->

The agent will now output something similar to the following when it calls the `AskUser` tool:

```text
Tool called: __ask_user__ with args {"message":"Which meme would you like me to explain?"}
```

For more information about Koog agent features, see [Features overview](../features-overview.md).

## Next steps

- Learn more about building [functional agents](functional-agents.md) and [graph-based agents](graph-based-agents.md)
