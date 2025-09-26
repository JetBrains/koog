# Functional agents

Functional agents are lightweight, non-graph agents that you can control with a simple loop
that calls an LLM one or a few times, optionally invokes tools, and returns a final value without building a full strategy graph.

!!! tip
    If you are new to Koog and want to create the simplest agent, start with [Single-run agents](single-run-agents.md).

## Prerequisites

- You have a valid API key from the LLM provider used to implement an AI agent. For a list of all available providers, see [Overview](index.md).

!!! tip
    Use environment variables or a secure configuration management system to store your API keys.
    Avoid hardcoding API keys directly in your source code.

## Creating a functional agent

To create a minimal functional agent, do the following:

1) Include all necessary dependencies in your build configuration to use the `FunctionalAIAgent` class functionality:

    ```
    dependencies {
        implementation("ai.koog:koog-agents:VERSION")
    }
    ```

    For all available installation methods, see [Installation](index.md#installation).

2) Create an instance of the `FunctionalAIAgent` class and provide a prompt executor, LLM, prompt, and an input loop.
   returns a single assistant message as a string.

Here is an example of an agent that sends a user text to the LLM and returns a single assistant message as a string.

<!--- INCLUDE
import ai.koog.agents.core.agent.FunctionalAIAgent
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.executor.llms.all.simpleOllamaExecutor
import ai.koog.agents.core.agent.asAssistantMessage
import ai.koog.agents.core.agent.requestLLMMultiple
kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
--> 
```kotlin
val agent = FunctionalAIAgent<String, String>(
    promptExecutor = simpleOllamaAIExecutor(),
    model = OllamaModels.Meta.LLAMA_3_2,
    prompt = "You are a helpful assistant."
) { input ->
    val responses = requestLLMMultiple(input)
    responses.single().asAssistantMessage().content
}

val result = agent.run("Say hi in one sentence")
println(result)
```
<!--- KNIT example-functional-agent-01.kt -->

In the example, `requestLLMMultiple(input)` sends the user input and receives content of an assistant message.
If you want to return structured data, parse the content or use the [Structured output API](structured-output.md).

## Adding tools 

Agents use tools to complete specific tasks.
To configure tools, use the `toolRegistry` parameter that defines the tools available to the agent.

Here is an example of a Switch device with tools the model can call:

<!--- INCLUDE
import ai.koog.agents.core.agent.FunctionalAIAgent
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.executor.llms.all.simpleOllamaExecutor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.core.agent.containsToolCalls
import ai.koog.agents.core.agent.executeMultipleTools
import ai.koog.agents.core.agent.extractToolCalls
import ai.koog.agents.core.agent.sendMultipleToolResults
import ai.koog.agents.core.agent.asAssistantMessage
import ai.koog.agents.core.agent.requestLLMMultiple
kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
--> 
```kotlin
class Switch {
    private var on = false
    fun on() { on = true }
    fun off() { on = false }
    fun isOn() = on
}

class SwitchTools(private val sw: Switch) {
    fun turn_on() = run { sw.on(); "ok" }
    fun turn_off() = run { sw.off(); "ok" }
    fun state() = if (sw.isOn()) "on" else "off"
}

val sw = Switch()
val tools = ToolRegistry { tools(SwitchTools(sw).asTools()) }

val agent = FunctionalAIAgent<String, String>(
    promptExecutor = simpleOllamaAIExecutor(),
    model = OllamaModels.Meta.LLAMA_3_2,
    prompt = "You're responsible for running a Switch device and perform operations on it upon request.",
    toolRegistry = tools
) { input ->
    var responses = requestLLMMultiple(input)

    while (responses.containsToolCalls()) {
        val pending = extractToolCalls(responses)
        val results = executeMultipleTools(pending)
        responses = sendMultipleToolResults(results)
    }

    responses.single().asAssistantMessage().content
}

val result = agent.run("Turn switch on")
println(result)
println("Switch is ${if (sw.isOn()) "on" else "off"}")
```
<!--- KNIT example-functional-agent-02.kt -->

In the example, `requestLLMMultiple(input)` sends the user input. The `containsToolCalls()` method detects tool call messages from the LLM.
If the LLM returns tool calls, for each tool call, the agent does the following:

1. The `extractToolCalls()` method reads, which tools to run and with what arguments. 
2. The `executeMultipleTools()` method runs the tools and returns the results. 
3. The `sendMultipleToolResults()` method sends the results back to the LLM and gets the next response. 

Then the `single()` method returns the content of the assistant message.

You can perform input validation inside your tool methods and return clear error messages when arguments are invalid,
so the LLM can self-correct on the next turn.

To learn more about tools, see [Tools overview](tools-overview.md).

## Observing and extending behavior with features

You can install features to observe or extend your agent. 

Here is an example that prints every tool call to the console:

<!--- INCLUDE
import ai.koog.agents.core.agent.FunctionalAIAgent
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.executor.llms.all.simpleOllamaExecutor
import ai.koog.agents.core.agent.containsToolCalls
import ai.koog.agents.core.agent.executeMultipleTools
import ai.koog.agents.core.agent.extractToolCalls
import ai.koog.agents.core.agent.requestLLMMultiple
import ai.koog.agents.core.agent.sendMultipleToolResults
import ai.koog.agents.core.agent.asAssistantMessage
import ai.koog.agents.features.eventHandler.feature.EventHandler
kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
-->

```kotlin
val observed = FunctionalAIAgent<String, String>(
    promptExecutor = simpleOllamaAIExecutor(),
    model = OllamaModels.Meta.LLAMA_3_2,
    prompt = "...",
    toolRegistry = tools,
    featureContext = {
        install(EventHandler) {
            onToolCall { e -> println("Tool called: ${'$'}{e.tool.name}, args: ${'$'}{e.toolArgs}") }
        }
    }
) { input ->
    var responses = requestLLMMultiple(input)
    while (responses.containsToolCalls()) {
        val pending = extractToolCalls(responses)
        val results = executeMultipleTools(pending)
        responses = sendMultipleToolResults(results)
    }
    responses.single().asAssistantMessage().content
}
```
<!--- KNIT example-functional-agent-03.kt -->

For more information about features, see [Features overview](features-overview.md).

## Handling long-running conversations

Long-running conversations can exceed the model context window.
To reduce its size, you can track token usage and compress history as needed.

<!--- INCLUDE
import ai.koog.agents.core.agent.FunctionalAIAgent
import ai.koog.prompt.executor.clients.openai.OllamaModels
import ai.koog.prompt.executor.llms.all.simpleOllamaExecutor
import ai.koog.agents.core.agent.containsToolCalls
import ai.koog.agents.core.agent.executeMultipleTools
import ai.koog.agents.core.agent.extractToolCalls
import ai.koog.agents.core.agent.sendMultipleToolResults
import ai.koog.agents.core.agent.latestTokenUsage
import ai.koog.agents.core.agent.asAssistantMessage
import ai.koog.agents.core.agent.compressHistory
import ai.koog.agents.core.agent.requestLLMMultiple
import ai.koog.agents.features.eventHandler.feature.EventHandler
kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val observed = FunctionalAIAgent<String, String>(
            promptExecutor = simpleOllamaAIExecutor(),
            model = OllamaModels.Meta.LLAMA_3_2,
            prompt = "...",
            toolRegistry = tools,
            featureContext = {
                install(EventHandler) {
                    onToolCall { e -> println("Tool called: ${'$'}{e.tool.name}, args: ${'$'}{e.toolArgs}") }
                }
            }
        ) { input ->
-->
<!--- SUFFIX
        }
    }
}
-->

```kotlin
var responses = requestLLMMultiple(input)

while (responses.containsToolCalls()) {
    if (latestTokenUsage() > 100_000) {
        compressHistory()
    }
    val pending = extractToolCalls(responses)
    val results = executeMultipleTools(pending)
    responses = sendMultipleToolResults(results)
}
```
<!--- KNIT example-functional-agent-04.kt -->

For more information about history compression, see [History compression](history-compression.md).

## Concurrency and lifecycle

`FunctionalAIAgent` prevents concurrent runs on the same instance.
If you need to process multiple requests in parallel, create a fresh agent instance per request or await completion of the current run.

!!! tip
    One agent instance per request. Do not share the same `FunctionalAIAgent` across parallel coroutines.

If you see an error like `“Agent is already running”`, it means two runs overlapped on the same instance.

## Returning custom output types

You can return a data class from your loop by changing the output type parameter.

```kotlin
data class MyResult(val title: String, val state: String)

val agent = FunctionalAIAgent<String, MyResult>(
    promptExecutor = simpleOllamaAIExecutor(),
    model = OllamaModels.Meta.LLAMA_3_2,
    prompt = "Summarize the status as JSON with fields: title, state."
) { input ->
    val responses = requestLLMMultiple(input)
    val text = responses.single().asAssistantMessage().content

    // Parse or map to your schema; alternatively use Structured output API
    val json = JSONObject(text)
    MyResult(
        title = json.getString("title"),
        state = json.getString("state")
    )
}
```

Alternatively, you can use the [Structured output API](structured-output.md) for safer schema‑based parsing.

## Troubleshooting notes

- Empty or unexpected model output
    - Check your system prompt; be explicit about the format you expect.
    - Print intermediate responses (for example, via EventHandler) to see what the LLM returns.
    - Consider adding a couple of few‑shot examples.
- Loop never ends
    - Break when there are no tool calls.
    - Add guards: a max loop count and a timeout.
- Context overflows
    - Watch `latestTokenUsage()` and call `compressHistory()` as needed.
- “Agent is already running”
    - Do not share one agent instance across parallel coroutines. Create a new agent per run or await completion.
