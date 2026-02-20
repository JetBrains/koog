# LLM-based planners

LLM-based planners use LLMs to generate and evaluate plans.
They operate on a string-based state and execute steps through LLM requests.
String-based state means that the agent state is a single string.
At every step, the agent accepts an initial state string and returns the final state string as the result.

??? note "Prerequisites"

    --8<-- "quickstart-snippets.md:prerequisites"

    --8<-- "quickstart-snippets.md:dependencies"

    --8<-- "quickstart-snippets.md:api-key"

    Examples on this page assume that you have set the `OPENAI_API_KEY` environment variable.

Koog provides two simple planners:

- [SimpleLLMPlanner](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner.llm/-simple-l-l-m-planner/index.html)
  generates a plan only once at the very beginning and then follows the plan until it is completed.
  To include replanning, extend `SimpleLLMPlanner` and override the `assessPlan` method,
  indicating when the agent should replan.
- [SimpleLLMWithCriticPlanner](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner.llm/-simple-l-l-m-with-critic-planner/index.html)
  implements the `assessPlan` method that uses an LLM to check the validity of the plan via an LLM request
  and assess whether the agent should replan.

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
<!--- KNIT example-llm-based-planners-01.kt -->

## Next steps

- Learn about [GOAP agents](goap-agents.md)
