# Planner agents

Planner agents are AI agents that can plan and execute multi-step tasks through iterative planning cycles. 
They continuously build or update plans, execute steps, and check if goals have been achieved.

Planner agents are suitable for complex tasks that require breaking down a high-level goal into smaller, actionable steps, and adapting the plan based on the results of each step.

## Prerequisites

Before you start, make sure that you have the following:

- A working Kotlin/JVM project.
- Java 17+ installed.
- A valid API key from the LLM provider used to implement an AI agent. For a list of all available providers, refer to [LLM providers](llm-providers.md).

!!! tip
    Use environment variables or a secure configuration management system to store your API keys.
    Avoid hardcoding API keys directly in your source code.

## Add dependencies

To use planner agents, include the following dependencies in your build configuration:

```
dependencies {
    implementation("ai.koog:koog-agents:$koog_version")
    implementation("ai.koog.agents:agents-planner:$koog_version")
    // Include Ktor client dependency explicitly
    implementation("io.ktor:ktor-client-cio:$ktor_version")
}
```

For all available installation methods, see [Install Koog](getting-started.md#install-koog).

## How planner agents work

Planner agents operate through an iterative planning cycle:

1. **Build a plan**: The planner creates or updates a plan based on the current state
2. **Execute a step**: The planner executes a single step from the plan, updating the state
3. **Check completion**: The planner determines if the goal has been achieved by checking the state against the goal condition
4. **Repeat**: If the goal is not achieved, the cycle repeats from step 1

## Simple LLM-based planners

Simple LLM-based planners use LLMs to generate and evaluate plans. 
They operate on a string state, i.e., just a single `String`, and execute steps through LLM requests.
Out-of-the-box, Koog provides two simple planners: `SimpleLLMPlanner` and `SimpleLLMWithCriticPlanner`:

<!--- INCLUDE
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.agents.planner.llm.SimpleLLMPlanner
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

suspend fun main() {
-->
<!--- SUFFIX
}
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

// Run the agent with a task
val result = agent.run("Create a plan to organize a team meeting")
println(result)
```
<!--- KNIT example-planner-01.kt -->


## GOAP (Goal-Oriented Action Planning)

GOAP is an algorithmic planning approach that uses A* search to find optimal action sequences.
Instead of using an LLM to generate plans, GOAP automatically discovers action sequences based on predefined goals and actions.

### Key concepts

GOAP planners work with three main concepts:

- **State**: Represents the current state of the world
- **Actions**: Define what can be done, including preconditions, effects (beliefs), costs, and execution logic
- **Goals**: Define target conditions, heuristic costs, and value functions

The planner uses A* search to find the sequence of actions that satisfies the goal condition while minimizing total cost.

### Creating a GOAP agent

To create a GOAP agent, you need to:

1. Define your state type
2. Define actions with preconditions and effects
3. Define goals with completion conditions
4. Create the GOAP planner using the DSL
5. Wrap it in a planner strategy and agent

In the following example, GOAP handles the high-level planning (outline → draft → review → publish),
while the LLM performs the actual content generation within each action.

<!--- INCLUDE
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.agents.planner.goap.goap
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import kotlin.reflect.typeOf

suspend fun main() {
-->
<!--- SUFFIX
}
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

val result = agent.run(ContentState(topic = "The Future of AI in Software Development"))
println("Final state: $result")
```
<!--- KNIT example-planner-02.kt -->


## Advanced GOAP features

### Custom cost functions

You can define custom cost functions for actions and goals to guide the planner:

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

### State beliefs vs actual execution

GOAP distinguishes between beliefs (optimistic predictions) and actual execution:

- **Belief**: What the planner thinks will happen (used for planning)
- **Execution**: What actually happens (used for real state updates)

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
