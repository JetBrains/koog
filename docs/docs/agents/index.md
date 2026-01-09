# Agents

AI agents are autonomous systems that can reason, make decisions,
interact with the environment, and take actions to achieve a specific goal.
In Koog, an AI agent is more than just a wrapper around an LLM;
it is a structured, type-safe state machine designed for the JVM ecosystem.

Koog agents are built around the following core concepts:

- A [prompt executor](../prompts/prompt-executors.md) manages and executes prompts,
  enabling the agent to interact with LLMs for reasoning and decision-making.
- A [strategy](../nodes-and-components.md) in the form of a directed graph defines the agent's workflow.
- An agent can use [tools](../tools-overview.md) to interact with external data sources and services.
- You can extend and enhance the functionality of AI agents using [features](../features-overview.md).

## Agent types

Depending on the task you need to perform, Koog provides several agent types:

- [Basic agents](basic-agents.md) are ideal for simple tasks that don't require any custom logic.
  These agents implement a predefined strategy that works for most common use cases.
- [Functional agents](functional-agents.md) enable you to prototype custom logic as a lambda function.
  However, they are not suitable for complex workflows and lack robust state management.
- [Graph-based agents](graph-based-agents.md) are the proper way to design complex workflows with Koog.
  Create a custom strategy for production agents to have full control and flexibility.
- [Planner agents](planner-agents.md) can plan and execute multistep tasks through iterative cycles.
  These agents implement a special type of strategy that accepts the initial state and outputs the final state.

## Agent configuration

!!! tip

    For information about creating and running a minimal agent,
    see [Getting started](../getting-started.md) and [Basic agents](basic-agents.md).

For simple agents, besides the mandatory prompt executor and language model,
you can specify the initial system prompt and some other parameters directly in the agent constructor:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant.",
    temperature = 0.7,
    maxIterations = 10
)
```
<!--- KNIT example-agent-config-01.kt -->

Alternatively, you can create an instance of [`AIAgentConfig`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.config/-a-i-agent-config/index.html)
to define the agent's behavior and parameters more granularly,
then pass it to the agent constructor:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.params.LLMParams
-->
```kotlin
val agentConfig = AIAgentConfig(
    prompt = prompt(
        id = "assistant",
        params = LLMParams(
            temperature = 0.7
        )
    ) {
        system("You are a helpful assistant.")
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 10
)

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    agentConfig = agentConfig
)
```
<!--- KNIT example-agent-config-02.kt -->

Here are the parameters of `AIAgentConfig`:

- `prompt` defines the initial [prompt](../prompts/prompt-creation/index.md) and [LLM parameters](../llm-parameters.md).

- `model` specifies the language model with which the agent interacts.
  You can use one of the predefined models or [create a custom model configuration](../model-capabilities.md#creating-a-model-llmodel-configuration).

- `maxAgentIterations` limits the maximum number of steps the agent can take before it terminates.
  Each step is a [node](../nodes-and-components.md) in the agent's workflow.

- `missingToolsConversionStrategy` defines a strategy for handling missing tools during agent execution.

[//]: # (TODO write about missing tools in the TOols section and link from here)

- `responseProcessor` can be used to define a custom response processor.
  For example, it can moderate and validate the response content, change the response format, or log the response.

[//]: # (TODO write about response processing somewhere?)
