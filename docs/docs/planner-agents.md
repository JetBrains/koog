# Planner agents

Planner agents are AI agents that can plan and execute multistep tasks through iterative planning cycles. 
They continuously build or update plans, execute steps, and check if goals have been achieved.

Planner agents are suitable for complex tasks that require breaking down a high-level goal into smaller, actionable 
steps and adapting the plan based on the results of each step.

## Prerequisites

Before you start, make sure that you have the following:

- A working Kotlin/JVM project.
- Java 17+ installed.
- A valid API key from the LLM provider that you use to implement an AI agent. For a list of all available providers, 
see [LLM providers](llm-providers.md).

!!! tip
    Use environment variables or a secure configuration management system to store your API keys.
    Avoid hardcoding API keys directly in your source code.

## Add dependencies

To use planner agents, include the following dependencies in your build configuration:

```
dependencies {
    implementation("ai.koog:koog-agents:VERSION")
}
```

For all available installation methods, see [Install Koog](getting-started.md#install-koog).

## How planner agents work

Planner agents operate through an iterative planning cycle:

1. **Build a plan**: The planner creates or updates a plan based on the current state.
2. **Execute a step**: The planner executes a single step from the plan, updating the state.
3. **Check completion**: The planner determines if the goal has been achieved by checking the state against the goal condition.
4. **Repeat**: If the goal is not achieved, the cycle repeats from the first step.

```mermaid
graph LR
  A[Build or update a plan] --> B["Execute a step <br> (update state)"]
  B --> C["Check completion (compare state to goal)"]
  C -->|Goal achieved| D[[Done]]
  C -->|"Repeat <br> (goal not achieved)"| A
```

## Simple LLM-based planners

Simple LLM-based planners use LLMs to generate and evaluate plans. 
They operate on a string-based state and execute steps through LLM requests. String-based state means that the agent
state is noted as a single string, where the agent accepts an initial state string and returns the final state string as
the result.

Out of the box, Koog provides two simple planners: 

- `SimpleLLMPlanner`: Generates a plan only once at the very beginning and then follows the plan until it is completed. 
It includes a replanning logic, but to use it you need to extend `SimpleLLMPlanner` and override the `assessPlan` method,
indicating when the agent should replan.
- `SimpleLLMWithCriticPlanner`: Implements the `assessPlan` method that uses an LLM. The method checks the validity of 
the plan with an LLM and assesses whether the agent should replan.

The following example shows how to create a simple planner agent using `SimpleLLMPlanner`:

<!--- INCLUDE
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.agents.planner.llm.SimpleLLMPlanner
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
-->
```kotlin
// Create the planner
val planner = SimpleLLMPlanner()

// Wrap it in a planner strategy
val strategy = AIAgentPlannerStrategy(
    name = "simple-planner",
    planner = planner
)

// Configure the agent
val agentConfig = AIAgentConfig(
    prompt = prompt("planner") {
        system("You are a helpful planning assistant.")
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 50
)

// Create the planner agent
val agent = PlannerAIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    strategy = strategy,
    agentConfig = agentConfig
)

suspend fun main() {
    // Run the agent with a task
    val result = agent.run("Create a plan to organize a team meeting")
    println(result)
}
```
<!--- KNIT example-planner-01.kt -->


## GOAP (Goal-Oriented Action Planning)

GOAP is an algorithmic planning approach that uses A* search to find optimal action sequences.
Instead of using an LLM to generate plans, GOAP automatically discovers action sequences based on predefined goals and actions. In Koog, GOAP is implemented through a DSL that lets you define goals and actions declaratively.

### Key concepts

GOAP planners work with three main concepts:

- **State**: Represents the current state of the world.
- **Actions**: Define what can be done, including preconditions, effects (beliefs), costs, and execution logic. 
Represented by the [action](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner.goap/-g-o-a-p-planner-builder/action.html) function.
- **Goals**: Define target conditions, heuristic costs, and value functions. Defined using the [goal](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner.goap/-g-o-a-p-planner-builder/goal.html) function.

The planner uses A* search to find the sequence of actions that satisfies the goal condition while minimizing total cost.

### Creating a GOAP agent

To create a GOAP agent, you need to:

1. Define your state type. State type is usually modeled as a data class.
2. Define actions with preconditions and beliefs. The planner then selects individual actions and their sequence.
Each action includes a precondition that must hold true for the action to be executed and a belief that defines the
predicted outcome. For more information about beliefs, see [State beliefs compared to actual execution](#state-beliefs-compared-to-actual-execution). 
3. Define goals with completion conditions.
4. Create the GOAP planner using the DSL.
5. Wrap it in a planner strategy and agent.

In the following example, GOAP handles the high-level planning for creating an article (outline → draft → review → 
publish), while the LLM performs the actual content generation within each action.

<!--- INCLUDE
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.dsl.extension.requestLLM
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.agents.planner.goap.goap
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import kotlin.reflect.typeOf
-->
```kotlin
// Define a state for content creation
data class ContentState(
    val topic: String,
    val hasOutline: Boolean = false,
    val outline: String = "",
    val hasDraft: Boolean = false,
    val draft: String = "",
    val hasReview: Boolean = false,
    val isPublished: Boolean = false
)

// Create GOAP planner with LLM-powered actions
val planner = goap<ContentState>(typeOf<ContentState>()) {
    // Define actions with preconditions and beliefs
    action(
        name = "Create outline",
        precondition = { state -> !state.hasOutline },
        belief = { state -> state.copy(hasOutline = true, outline = "Outline") },
        cost = { 1.0 }
    ) { ctx, state ->
        // Use LLM to create the outline
        val response = ctx.llm.writeSession {
            appendPrompt {
                user("Create a detailed outline for an article about: ${state.topic}")
            }
            requestLLM()
        }
        state.copy(hasOutline = true, outline = response.content)
    }

    action(
        name = "Write draft",
        precondition = { state -> state.hasOutline && !state.hasDraft },
        belief = { state -> state.copy(hasDraft = true, draft = "Draft") },
        cost = { 2.0 }
    ) { ctx, state ->
        // Use LLM to write the draft
        val response = ctx.llm.writeSession {
            appendPrompt {
                user("Write an article based on this outline:\n${state.outline}")
            }
            requestLLM()
        }
        state.copy(hasDraft = true, draft = response.content)
    }

    action(
        name = "Review content",
        precondition = { state -> state.hasDraft && !state.hasReview },
        belief = { state -> state.copy(hasReview = true) },
        cost = { 1.0 }
    ) { ctx, state ->
        // Use LLM to review the draft
        val response = ctx.llm.writeSession {
            appendPrompt {
                user("Review this article and suggest improvements:\n${state.draft}")
            }
            requestLLM()
        }
        println("Review feedback: ${response.content}")
        state.copy(hasReview = true)
    }

    action(
        name = "Publish",
        precondition = { state -> state.hasReview && !state.isPublished },
        belief = { state -> state.copy(isPublished = true) },
        cost = { 1.0 }
    ) { ctx, state ->
        println("Publishing article...")
        state.copy(isPublished = true)
    }
    // Define the goal with a completion condition
    goal(
        name = "Published article",
        description = "Complete and publish the article",
        condition = { state -> state.isPublished }
    )
}

// Create and run the agent
val strategy = AIAgentPlannerStrategy("content-planner", planner)
val agentConfig = AIAgentConfig(
    prompt = prompt("writer") {
        system("You are a professional content writer.")
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 20
)

val agent = PlannerAIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    strategy = strategy,
    agentConfig = agentConfig
)

suspend fun main() {
    val result = agent.run(ContentState(topic = "The Future of AI in Software Development"))
    println("Final state: $result")
}
```
<!--- KNIT example-planner-02.kt -->


## Advanced GOAP features

### Custom cost functions

As A* search uses cost as a factor in finding the optimal sequence of actions, you can define custom cost functions for actions and goals to guide the planner:

```kotlin
action(
    name = "Expensive operation",
    precondition = { true },
    belief = { state -> state.copy(operationDone = true) },
    cost = { state ->
        // Dynamic cost based on state
        if (state.hasOptimization) 1.0 else 10.0
    }
) { ctx, state ->
    // Execute action
    state.copy(operationDone = true)
}
```

### State beliefs compared to actual execution

GOAP distinguishes between the concepts of beliefs (optimistic predictions) and actual execution:

- **Belief**: What the planner thinks will happen, used for planning.
- **Execution**: What actually happens, used for real state updates.

This allows the planner to make plans based on expected outcomes while handling actual results properly:

```kotlin
action(
    name = "Attempt complex task",
    precondition = { state -> !state.taskComplete },
    belief = { state ->
        // Optimistic belief: task will succeed
        state.copy(taskComplete = true)
    },
    cost = { 5.0 }
) { ctx, state ->
    // Actual execution might fail or have different results
    val success = performComplexTask()
    state.copy(
        taskComplete = success,
        attempts = state.attempts + 1
    )
}
```
