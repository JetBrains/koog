# Predefined agent strategies

To make agent implementations easier, Koog provides predefined agent strategies for common agent use cases.
The following predefined strategies are available:

- [Chat agent strategy](#chat-agent-strategy)
- [ReAct strategy](#react-strategy)

## Chat agent strategy

The Chat agent strategy is designed for executing a chat interaction process.
It orchestrates interactions between different stages, nodes, and tools to handle user input, execute tools, and provide responses in a chat-like manner.

### Overview

The Chat agent strategy implements a pattern where the agent:

1. Receives user input
2. Processes the input using an LLM
3. Either calls a tool or provides a direct response
4. Processes tool results and continues the conversation
5. Provides feedback if the LLM tries to respond with plain text instead of using tools

This approach creates a conversational interface where the agent can use tools to fulfill user requests.

### Setup and dependencies

The implementation of Chat agent strategy in Koog is done through the `chatAgentStrategy` function. To make the function available in your agent code, add the following dependency import:

```
ai.koog.agents.ext.agent.chatAgentStrategy
```

To use the strategy, create an AI agent following the pattern below:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.agents.ext.agent.chatAgentStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels

val apiKey = System.getenv("OPENAI_API_KEY") ?: error("Please set OPENAI_API_KEY environment variable")
val promptExecutor =simpleOpenAIExecutor(apiKey)
val toolRegistry = ToolRegistry.EMPTY
val model =  OpenAIModels.Chat.O4Mini
-->
```kotlin
val chatAgent = AIAgent(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    llmModel = model,
    // Set chatAgentStrategy as the agent strategy
    strategy = chatAgentStrategy()
)
```
<!--- KNIT example-predefined-strategies-01.kt -->

### When to use the Chat agent strategy

The Chat agent strategy is particularly useful for:

- Building conversational agents that need to use tools
- Creating assistants that can perform actions based on user requests
- Implementing chatbots that need to access external systems or data
- Scenarios where you want to enforce tool usage rather than plain text responses

### Example

Here is a code sample of an AI agent that implements the predefined Chat agent strategy (`chatAgentStrategy`) and tools that the agent may use:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.agents.ext.agent.chatAgentStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser

typealias searchTool = AskUser
typealias weatherTool = SayToUser

val apiKey = System.getenv("OPENAI_API_KEY") ?: error("Please set OPENAI_API_KEY environment variable")
val promptExecutor =simpleOpenAIExecutor(apiKey)
val toolRegistry = ToolRegistry.EMPTY
val model =  OpenAIModels.Chat.O4Mini
-->
```kotlin
val chatAgent = AIAgent(
    promptExecutor = promptExecutor,
    llmModel = model,
    // Use chatAgentStrategy as the agent strategy
    strategy = chatAgentStrategy(),
    // Add tools the agent can use
    toolRegistry = ToolRegistry {
        tool(searchTool)
        tool(weatherTool)
    }
)

suspend fun main() { 
    // Run the agent with a user query
    val result = chatAgent.run("What's the weather like today and should I bring an umbrella?")
}
```
<!--- KNIT example-predefined-strategies-02.kt -->

## ReAct strategy

The ReAct (Reasoning and Acting) strategy is an AI agent strategy that alternates between reasoning and execution stages to dynamically process tasks and request output from a Large Language Model (LLM).

### Overview

The ReAct strategy implements a pattern where the agent:

1. Reasons about the current state and plans the next steps
2. Takes actions based on that reasoning
3. Observes the results of those actions
4. Repeats the cycle

This approach combines the strengths of reasoning (thinking through problems step by step) and acting (executing tools to gather information or perform operations).

### Flow diagram

Here is the flow diagram of the ReAct strategy:

![Koog flow diagram](img/koog-react-diagram-light.png#only-light)
![Koog flow diagram](img/koog-react-diagram-dark.png#only-dark)

### Setup and dependencies

The implementation of ReAct strategy in Koog is done through the `reActStrategy` function. To make the function
available in your agent code, add the following dependency import:

```
ai.koog.agents.ext.agent.reActStrategy
```
<!--- KNIT example-predefined-strategies-03.kt -->

To use the strategy, create an AI agent following the pattern below:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels

val apiKey = System.getenv("OPENAI_API_KEY") ?: error("Please set OPENAI_API_KEY environment variable")
val promptExecutor = simpleOpenAIExecutor(apiKey)
val toolRegistry = ToolRegistry.EMPTY
val model =  OpenAIModels.Chat.O4Mini
-->
```kotlin hl_lines="5-10"
val reActAgent = AIAgent(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    llmModel = model,
    // Set reActStrategy as the agent strategy
    strategy = reActStrategy(
        // Set optional parameter values
        reasoningInterval = 1,
        name = "react_agent"
    )
)
```
<!--- KNIT example-predefined-strategies-04.kt -->

### Parameters

The `reActStrategy` function takes the following parameters:

| Parameter           | Type   | Default  | Description                                                         |
|---------------------|--------|----------|---------------------------------------------------------------------|
| `reasoningInterval` | Int    | 1        | Specifies the interval for reasoning steps. Must be greater than 0. |
| `name`              | String | `re_act` | The name of the strategy.                                           |

### Example use case

Here is an example of how the ReAct strategy works with a simple banking agent:

#### 1. User input

The user sends the initial prompt. For example, this can be a question such as `How much did I spend last month?`.

#### 2. Reasoning

The agent performs the initial reasoning by taking the user input and the reasoning prompt. The reasoning can look as
follows:

```text
I need to follow these steps:
1. Get all transactions from last month
2. Filter out deposits (positive amounts)
3. Calculate total spending
```

#### 3. Action and execution, phase 1

Based on the action items that the agent defined in the previous step, it runs a tool to get all transactions
from the previous month.

In this case, the tool to run is `get_transactions`, along with the defined `startDate` and `endDate` arguments that
match the request to get all transactions during the previous month:

```text
{tool: "get_transactions", args: {startDate: "2025-05-19", endDate: "2025-06-18"}}
```

The tool returns a result that can look as follows:

```text
[
  {date: "2025-05-25", amount: -100.00, description: "Grocery Store"},
  {date: "2025-05-31", amount: +1000.00, description: "Salary Deposit"},
  {date: "2025-06-10", amount: -500.00, description: "Rent Payment"},
  {date: "2025-06-13", amount: -200.00, description: "Utilities"}
]
```

#### 4. Reasoning

With the result returned by the tool, the agent performs reasoning again to determine the next steps in its flow:

```text
I have the transactions. Now I need to:
1. Remove the salary deposit of +1000.00
2. Sum up the remaining transactions
```

#### 5. Action and execution, phase 2

Based on the previous reasoning step, the agent calls the `calculate_sum` tool that sums up the amounts provided as
tool arguments. As the reasoning also resulted in the action point of removing the positive amount from transactions,
the amounts provided as tool arguments are only the negative ones:

```text
{tool: "calculate_sum", args: {amounts: [-100.00, -500.00, -200.00]}}
```

The tool returns the final result:

```text
-800.00
```

#### 6. Final response

The agent returns the final response (assistant message) that includes the calculated sum:

```text
You spent $800.00 last month on groceries, rent, and utilities.
```

### When to use the ReAct strategy

The ReAct strategy is particularly useful for:

- Complex tasks requiring multistep reasoning
- Scenarios where the agent needs to gather information before providing a final answer
- Problems that benefit from breaking down into smaller steps
- Tasks requiring both analytical thinking and tool usage

### Example

Here is a code sample of an AI agent that implements the predefined ReAct strategy (`reActStrategy`) and tools that
the agent may use:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser

typealias Input = String

typealias getTransactions = AskUser
typealias calculateSum = SayToUser

val apiKey = System.getenv("OPENAI_API_KEY") ?: error("Please set OPENAI_API_KEY environment variable")
val promptExecutor = simpleOpenAIExecutor(apiKey)
val toolRegistry = ToolRegistry.EMPTY
val model =  OpenAIModels.Chat.O4Mini
-->
```kotlin
val bankingAgent = AIAgent(
    promptExecutor = promptExecutor,
    llmModel = model,
    // Use reActStrategy as the agent strategy
    strategy = reActStrategy(
        reasoningInterval = 1,
        name = "banking_agent"
    ),
    // Add tools the agent can use
    toolRegistry = ToolRegistry {
        tool(getTransactions)
        tool(calculateSum)
    }
)

suspend fun main() { 
    // Run the agent with a user query
    val result = bankingAgent.run("How much did I spend last month?")
}
```
<!--- KNIT example-predefined-strategies-05.kt -->
