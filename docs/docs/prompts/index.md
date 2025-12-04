# Prompts

Prompts are instructions for Large Language Models (LLMs) that guide them in generating responses.
They define the content and structure of your interactions with LLMs.
This section describes how to create and run prompts with Koog.

## Creating prompts

In Koog, all prompts are represented as [**Prompt**](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.dsl/-prompt/index.html)
objects. A Prompt object contains:

- **ID**: A unique identifier for the prompt.
- **Messages**: A list of messages that represent the conversation with the LLM.
- **Parameters**: Optional [LLM configuration parameters](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.params/-l-l-m-params/index.html)
  (such as temperature, tool choice, and other).

All Prompt objects are structured prompts defined using the Kotlin DSL, which lets you specify the structure of the conversation.

!!! note
    AI agents let you provide a simple text prompt instead of a Prompt object.
    They automatically convert it to the Prompt and send to the LLM for execution.
    This is useful for a [basic agent](basic-agents.md) that only needs to run a single request.


<div class="grid cards" markdown>

-   :material-code-braces:{ .lg .middle } [**Structured prompts**](structured-prompts.md)

    ---

    Create type-safe structured prompts for complex multi-turn conversations.

-   :material-multimedia:{ .lg .middle } [**Multimodal inputs**](multimodal-inputs.md)

    ---

    Send images, audio, video, and documents along with text in your structured prompts.

</div>

## Running prompts

Koog provides two levels of abstraction for running prompts against LLMs: LLM clients and prompt executors.
They only accept Prompt objects and can be used for direct prompt execution, without an AI agent.
The execution flow is the same for both clients and executors:

```mermaid
flowchart TB
    A([Prompt built with Kotlin DSL])
    B{LLM client or prompt executor}
    C[LLM provider]
    D([Response to your application])

    A -->|"passed to"| B
    B -->|"sends request"| C
    C -->|"returns response"| B
    B -->|"returns result"| D
```

<div class="grid cards" markdown>

-   :material-arrow-right-bold:{ .lg .middle } [**LLM clients**](lm-clients.md)

    ---

    Low‑level interfaces for direct interaction with specific LLM providers.
    Use when working with a single provider, and advanced lifecycle management is not required.

-   :material-swap-horizontal:{ .lg .middle } [**Prompt executors**](prompt-executors.md)

    ---

    High-level abstraction managing lifecycles of one or multiple LLM clients.
    Use when you need a unified API for running prompts across multiple providers,
    with dynamic switching between them and fallbacks.

</div>

If you want to run a simple text prompt, wrap it into a Prompt object with the Kotlin DSL,
or use an agent, which does this for you.
Here is the execution flow for an agent:

```mermaid
flowchart TB
    A([Your application])
    B{{Configured AI agent}}
    C["Text prompt"]
    D["Prompt object"]
    E{{Prompt executor}}
    F[LLM provider]

    A -->|"run() with text"| B
    B -->|"takes"| C
    C -->|"converted to"| D
    D -->|"sent via"| E
    E -->|"calls"| F
    F -->|"responds to"| E
    E -->|"result to"| B
    B -->|"result to"| A
```

```kotlin
// Create an agent
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o
)

// Run the agent
val result = agent.run("What is Koog?")
```

## Optimizing and handling failures

Koog provides mechanisms to optimize performance and handle failures when running prompts.

<div class="grid cards" markdown>

-   :material-cached:{ .lg .middle } [**Prompt caching**](prompt-caching.md)

    ---

    Cache LLM responses to optimize performance and reduce costs for repeated requests.

-   :material-shield-check:{ .lg .middle } [**Failure handling**](failure-handling.md)

    ---

    Built-in retries, timeouts, and automatic fallbacks to alternative providers.

</div>
