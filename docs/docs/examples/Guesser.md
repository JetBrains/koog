# Building a Number‑Guessing Agent with Koog

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/Guesser.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/Guesser.ipynb
){ .md-button }

Let’s build a small but fun agent that guesses a number you’re thinking of. We’ll lean on Koog’s tool-calling to ask targeted questions and converge using a classic binary search strategy. The result is an idiomatic Kotlin Notebook that you can drop straight into docs.

We’ll keep the code minimal and the flow transparent: a few tiny tools, a compact prompt, and an interactive CLI loop.

## Setup

This notebook assumes:
- You’re running in a Kotlin Notebook with Koog available.
- The environment variable `OPENAI_API_KEY` is set. The agent uses it via `simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))`.

Load the Koog kernel:


```kotlin
%useLatestDescriptors
%use koog
```

## Tools: asking targeted questions

Tools are small, well-described functions the LLM can call. We’ll provide three:
- `lessThan(value)`: “Is your number less than value?”
- `greaterThan(value)`: “Is your number greater than value?”
- `proposeNumber(value)`: “Is your number equal to value?” (used once the range is tight)

Each tool returns a simple "YES"/"NO" string. The helper `ask` implements a minimal Y/n loop and validates input. Descriptions via `@LLMDescription` help the model select tools correctly.


```kotlin
import ai.koog.agents.core.tools.annotations.Tool

class GuesserTool : ToolSet {

    @Tool
    @LLMDescription("Asks the user if his number is STRICTLY less than a given value.")
    fun lessThan(
        @LLMDescription("A value to compare the guessed number with.") value: Int
    ): String = ask("Is your number less than $value?", value)

    @Tool
    @LLMDescription("Asks the user if his number is STRICTLY greater than a given value.")
    fun greaterThan(
        @LLMDescription("A value to compare the guessed number with.") value: Int
    ): String = ask("Is your number greater than $value?", value)

    @Tool
    @LLMDescription("Asks the user if his number is EXACTLY equal to the given number. Only use this tool once you've narrowed down your answer.")
    fun proposeNumber(
        @LLMDescription("A value to compare the guessed number with.") value: Int
    ): String = ask("Is your number equal to $value?", value)

    fun ask(question: String, value: Int): String {
        print("$question [Y/n]: ")
        val input = readln()
        println(input)

        return when (input.lowercase()) {
            "", "y", "yes" -> "YES"
            "n", "no" -> "NO"
            else -> {
                println("Invalid input! Please, try again.")
                ask(question, value)
            }
        }
    }
}
```

## Tool Registry

Expose your tools to the agent. We also add a built‑in `SayToUser` tool so the agent can surface messages directly to the user.


```kotlin
val toolRegistry = ToolRegistry {
    tool(SayToUser)
    tools(GuesserTool())
}
```

## Agent configuration

A short, tool‑forward system prompt is all we need. We’ll suggest a binary search strategy and keep `temperature = 0.0` for stable, deterministic behavior. Here we use OpenAI’s reasoning model `GPT4oMini` for crisp planning.


```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4oMini,
    systemPrompt = """
            You are a number guessing agent. Your goal is to guess a number that the user is thinking of.
            
            Follow these steps:
            1. Start by asking the user to think of a number between 1 and 100.
            2. Use the less_than and greater_than tools to narrow down the range.
                a. If it's neither greater nor smaller, use the propose_number tool.
            3. Once you're confident about the number, use the propose_number tool to check if your guess is correct.
            4. If your guess is correct, congratulate the user. If not, continue guessing.
            
            Be efficient with your guessing strategy. A binary search approach works well.
        """.trimIndent(),
    temperature = 0.0,
    toolRegistry = toolRegistry
)
```

## Run it

- Think of a number between 1 and 100.
- Type `start` to begin.
- Answer the agent’s questions with `Y`/`Enter` for yes or `n` for no. The agent should zero in on your number in ~7 steps.


```kotlin
import kotlinx.coroutines.runBlocking

println("Number Guessing Game started!")
println("Think of a number between 1 and 100, and I'll try to guess it.")
println("Type 'start' to begin the game.")

val initialMessage = readln()
runBlocking {
    agent.run(initialMessage)
}
```

## How it works

- The agent reads the system prompt and plans a binary search.
- On each iteration it calls one of your tools: `lessThan`, `greaterThan`, or (when certain) `proposeNumber`.
- The helper `ask` collects your Y/n input and returns a clean "YES"/"NO" signal back to the model.
- When it gets confirmation, it congratulates you via `SayToUser`.

## Extend it

- Change the range (e.g., 1..1000) by tweaking the system prompt.
- Add a `between(low, high)` tool to reduce calls further.
- Swap models or executors (e.g., use an Ollama executor and a local model) while keeping the same tools.
- Persist guesses or outcomes to a store for analytics.

## Troubleshooting

- Missing key: ensure `OPENAI_API_KEY` is set in your environment.
- Kernel not found: make sure `%useLatestDescriptors` and `%use koog` executed successfully.
- Tool not called: confirm the `ToolRegistry` includes `GuesserTool()` and the names in the prompt match your tool functions.
