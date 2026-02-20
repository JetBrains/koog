# GOAP agents

GOAP is an algorithmic planning approach that uses [A* search] find optimal action sequences
that satisfy the goal conditions while minimizing the total cost.
Unlike [LLM-based planners](llm-based-planners.md) that use an LLM to generate plans,
a GOAP agent algorithmically discovers action sequences based on predefined goals and actions.

GOAP planners work with three main concepts:

- **State**: Represents the current state of the world.
- **Actions**: Define what can be done, including preconditions, effects (beliefs), costs, and execution logic.
- **Goals**: Define target conditions, heuristic costs, and value functions.

??? note "Prerequisites"

    --8<-- "quickstart-snippets.md:prerequisites"

    --8<-- "quickstart-snippets.md:dependencies"

    --8<-- "quickstart-snippets.md:api-key"

    Examples on this page assume that you have set the `OPENAI_API_KEY` environment variable.

In Koog, you define a GOAP agent using a DSL by declaratively specifying the goals and actions.

To create a GOAP agent, you need to:

1. Define the state as a data class with properties representing various aspects specific to your goal.
2. Create a [GOAPPlanner](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner.goap/-g-o-a-p-planner/index.html) instance using the [goap()](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner.goap/goap.html) function.
    1. Define actions with preconditions and beliefs using the [action()](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner.goap/-g-o-a-p-planner-builder/action.html) function.
    2. Define goals with completion conditions using the [goal()](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner.goap/-g-o-a-p-planner-builder/goal.html) function.
3. Wrap the planner with [AIAgentPlannerStrategy](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner/-a-i-agent-planner-strategy/index.html) and pass it to the [PlannerAIAgent](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner/-planner-a-i-agent/index.html) constructor.

!!! note

    The planner selects individual actions and their sequence.
    Each action includes a precondition that must hold true for the action to be executed
    and a belief that defines the predicted outcome.
    For more information about beliefs, see [State beliefs compared to actual execution](#state-beliefs-compared-to-actual-execution).

In the following example, GOAP handles high-level planning for creating an article
(outline → draft → review → publish),
while the LLM performs the actual content generation within each action.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.goap.GoapAgentState
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
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
): GoapAgentState<String, String>(topic) {
    override fun provideOutput(): String = draft
}

// Create GOAP planner with LLM-powered actions
val planner = AIAgentPlannerStrategy.goap("content-planner", ::ContentState) {
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
val agentConfig = AIAgentConfig(
    prompt = prompt("writer") {
        system("You are a professional content writer.")
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 20
)

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    strategy = planner,
    agentConfig = agentConfig
)

suspend fun main() {
    val result = agent.run("The Future of AI in Software Development")
    println("Final state: $result")
}
```
<!--- KNIT example-goap-agents-01.kt -->

## Custom cost functions

As [A* search] uses cost as a factor in finding the optimal sequence of actions,
you can define custom cost functions for actions and goals to guide the planner:

<!--- INCLUDE
import ai.koog.agents.planner.goap.GoapAgentState
import ai.koog.agents.planner.AIAgentPlannerStrategy

data class MyState(
    val topic: String,
    val operationDone: Boolean = true,
    val hasOptimization: Boolean = true
): GoapAgentState<String, String>(topic) {
    override fun provideOutput(): String = ""
}

val planner = AIAgentPlannerStrategy.goap("content-planner", ::MyState) {
----- SUFFIX
}   
-->
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
<!--- KNIT example-goap-agents-02.kt -->

## State beliefs compared to actual execution

GOAP distinguishes between the concepts of beliefs (optimistic predictions) and actual execution:

- **Belief**: What the planner thinks will happen, used for planning.
- **Execution**: What actually happens, used for real state updates.

This allows the planner to make plans based on expected outcomes while handling actual results properly:

<!--- INCLUDE
import ai.koog.agents.planner.goap.GoapAgentState
import ai.koog.agents.planner.AIAgentPlannerStrategy

data class MyState(
    val topic: String,
    val taskComplete: Boolean = true,
    val attempts: Int = 0
): GoapAgentState<String, String>(topic) {
    override fun provideOutput(): String = ""
}

fun performComplexTask(): Boolean = true

val planner = AIAgentPlannerStrategy.goap("content-planner", ::MyState) {
----- SUFFIX
}   
-->
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
<!--- KNIT example-goap-agents-03.kt -->

[A* search]: https://en.wikipedia.org/wiki/A*_search_algorithm
