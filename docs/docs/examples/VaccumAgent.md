# Build a Simple Vacuum Cleaner Agent

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/VaccumAgent.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/VaccumAgent.ipynb
){ .md-button }

In this notebook, we'll explore how to implement a basic reflex agent using the new Kotlin agents framework.
Our example will be the classic "vacuum world" problem —
a simple environment with two locations that can be clean or dirty, and an agent that needs to clean them.

First, let's understand our environment model:


```kotlin
import kotlin.random.Random

/**
 * Represents a simple vacuum world with two locations (A and B).
 *
 * The environment tracks:
 * - The current location of the vacuum agent ('A' or 'B')
 * - The cleanliness status of each location (true = dirty, false = clean)
 */
class VacuumEnv {
    var location: Char = 'A'
        private set

    private val status = mutableMapOf(
        'A' to Random.nextBoolean(),
        'B' to Random.nextBoolean()
    )

    fun percept(): Pair<Char, Boolean> = location to status.getValue(location)

    fun clean(): String {
        status[location] = false
        return "cleaned"
    }

    fun moveLeft(): String {
        location = 'A'
        return "move to A"
    }

    fun moveRight(): String {
        location = 'B'
        return "move to B"
    }

    fun isClean(): Boolean = status.values.all { it }

    fun worldLayout(): String = "${status.keys}"

    override fun toString(): String = "location=$location, dirtyA=${status['A']}, dirtyB=${status['B']}"
}
```

The VacuumEnv class models our simple world:
- Two locations are represented by characters 'A' and 'B'
- Each location can be either clean or dirty (randomly initialized)
- The agent can be at either location at any given time
- The agent can perceive its current location and whether it's dirty
- The agent can take actions: move to a specific location or clean the current location

## Creating Tools for Vacuum Agent
Now, let's define the tools our AI agent will use to interact with the environment:


```kotlin
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet


/**
 * Provides tools for the LLM agent to control the vacuum robot.
 * All methods either mutate or read from the VacuumEnv passed to the constructor.
 */
@LLMDescription("Tools for controlling a two-cell vacuum world")
class VacuumTools(private val env: VacuumEnv) : ToolSet {

    @Tool
    @LLMDescription("Returns current location and whether it is dirty")
    fun sense(): String {
        val (loc, dirty) = env.percept()
        return "location=$loc, dirty=$dirty, locations=${env.worldLayout()}"
    }

    @Tool
    @LLMDescription("Cleans the current cell")
    fun clean(): String = env.clean()

    @Tool
    @LLMDescription("Moves the agent to cell A")
    fun moveLeft(): String = env.moveLeft()

    @Tool
    @LLMDescription("Moves the agent to cell B")
    fun moveRight(): String = env.moveRight()
}
```

The `VacuumTools` class creates an interface between our LLM agent and the environment:

- It implements `ToolSet` from the Kotlin AI Agents framework
- Each tool is annotated with `@Tool` and has a description for the LLM
- The tools allow the agent to sense its environment and take actions
- Each method returns a string that describes the outcome of the action

## Setting Up the Agent
Next, we'll configure and create our AI agent:


```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.agent.chatAgentStrategy
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.params.LLMParams


val env = VacuumEnv()
val apiToken = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable not set")
val executor = simpleOpenAIExecutor(apiToken = apiToken)

val toolRegistry = ToolRegistry {
    tool(SayToUser)
    tool(AskUser)
    tools(VacuumTools(env).asTools())
}

val systemVacuumPrompt = """
    You are a reflex vacuum-cleaner agent living in a two-cell world labelled A and B.
    Your goal: make both cells clean, using the provided tools.
    First, call sense() to inspect where you are. Then decide: if dirty → clean(); else moveLeft()/moveRight().
    Continue until both cells are clean, then tell the user "done".
    Use sayToUser to inform the user about each step.
""".trimIndent()

val agentConfig = AIAgentConfig(
    prompt = prompt("chat", params = LLMParams(temperature = 1.0)) {
        system(systemVacuumPrompt)
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 50,
)

val agent = AIAgent(
    promptExecutor = executor,
    strategy = chatAgentStrategy(),
    agentConfig = agentConfig,
    toolRegistry = toolRegistry
)
```

In this setup:

1. We create an instance of our environment
2. We set up a connection to OpenAI's GPT-4o model
3. We register the tools our agent can use
4. We define a system prompt that gives the agent its goal and behavior rules
5. We create the agent using the `AIAgent` constructor with a chat strategy

## Running the Agent

Finally, let's run our agent:


```kotlin
import kotlinx.coroutines.runBlocking

runBlocking {
    agent.run("Start cleaning, please")
}
```

    Agent says: Currently in cell A. It's already clean.
    Agent says: Moved to cell B. It's already clean.


When we run this code:

1. The agent receives the initial prompt to start cleaning
2. It uses its tools to sense the environment and make decisions
3. It continues cleaning until both cells are clean
4. Throughout the process, it keeps the user informed about what it's doing



```kotlin
// Finally we can validate that the work is finished by printing the env state

env
```




    location=B, dirtyA=false, dirtyB=false


