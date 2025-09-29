# FunctionalAIAgent: How to build a single‑run agent step by step

FunctionalAIAgent is a lightweight, non‑graph agent that you control with a simple loop. Use it when you want to:
- Call an LLM once or a few times in a custom loop;
- Optionally call tools between LLM turns;
- Return a final value (string, data class, etc.) without building a full strategy graph.

What you’ll do in this guide:
1) Create a “Hello, World” FunctionalAIAgent.
2) Add a tool and let the agent call it.
3) Add a feature (event handler) to observe behavior.
4) Keep context under control with history compression.
5) Learn common recipes, pitfalls, and FAQs.

## 1) Prerequisites
You need a PromptExecutor (the object that actually talks to your LLM). For local experimenting, you can use the Ollama executor:

```kotlin
val exec = simpleOllamaAIExecutor()
```

You also need to pick a model, for example:

```kotlin
val model = OllamaModels.Meta.LLAMA_3_2
```

That’s it — we’ll inject both into the agent factory.


## 2) Your first agent (Hello, World)
Goal: Send the user’s text to the LLM and return a single assistant message as a string.

```kotlin
val agent = functionalAIAgent<String, String>(
    prompt = "You are a helpful assistant.",
    promptExecutor = exec,
    model = model
) { input ->
    val responses = requestLLMMultiple(input)
    responses.single().asAssistantMessage().content
}

val result = agent.run("Say hi in one sentence")
println(result)
```

What happens?
- requestLLMMultiple(input) sends the user input and receives one or more assistant messages.
- We return the only message’s content (typical one‑shot flow).

Tip: If you want to return structured data, parse the content or use the Structured Data API.


## 3) Add tools (how the agent calls your functions)
Goal: Let the model operate a tiny device via tools.

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

val toolAgent = functionalAIAgent<String, String>(
    prompt = "You're responsible for running a Switch device and perform operations on it by request.",
    promptExecutor = exec,
    model = model,
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

val out = toolAgent.run("Turn switch on")
println(out)
println("Switch is ${if (sw.isOn()) "on" else "off"}")
```

How it works
- containsToolCalls() detects tool call messages from the LLM.
- extractToolCalls(...) reads which tools to run and with what args.
- executeMultipleTools(...) runs them against your ToolRegistry.
- sendMultipleToolResults(...) sends results back to the LLM and gets the next response.


## 4) Observe behavior with features (EventHandler)
Goal: Print every tool call to the console.

```kotlin
val observed = functionalAIAgent<String, String>(
    prompt = "...",
    promptExecutor = exec,
    model = model,
    toolRegistry = tools,
    featureContext = {
        install(EventHandler) {
            onToolCallStarting { e -> println("Tool called: ${'$'}{e.tool.name}, args: ${'$'}{e.toolArgs}") }
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

Other features you can install this way include streaming tokens and tracing; see the related docs in the sidebar.


## 5) Keep context under control (history compression)
Long conversations can exceed the model’s context window. Use the token usage to decide when to compress history:

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

Use a threshold appropriate for your model and prompt size.


## Common recipes
- Return structured output
  - Ask the LLM to format JSON and parse it; or use Structured Data API.
- Validate tool inputs
  - Perform validation in tool functions and return clear error messages.
- One agent instance per request
  - Each agent instance is single‑run at a time. Create new instances if you need concurrency.
- Custom Output type
  - Change functionalAIAgent<String, MyResult> and return a data class from the loop.


## Troubleshooting & pitfalls
- “Agent is already running”
  - FunctionalAIAgent prevents concurrent runs on the same instance. Don’t share one instance across parallel coroutines; create a fresh agent per run or await completion.
- Empty or unexpected model output
  - Check your system prompt. Print intermediate responses. Consider adding few‑shot examples.
- Loop never ends
  - Ensure you break when there are no tool calls; add guards/timeouts for safety.
- Context overflows
  - Watch latestTokenUsage() and call compressHistory().


## Reference (quick)
Constructors

```kotlin
fun <Input, Output> functionalAIAgent(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfigBase,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    loop: suspend AIAgentFunctionalContext.(input: Input) -> Output
): AIAgent<Input, Output>

fun <Input, Output> functionalAIAgent(
    promptExecutor: PromptExecutor,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    prompt: String = "",
    model: LLModel = OpenAIModels.Chat.GPT4o,
    featureContext: FeatureContext.() -> Unit = {},
    func: suspend AIAgentFunctionalContext.(input: Input) -> Output,
): AIAgent<Input, Output>
```

Important types
- FunctionalAIAgent<Input, Output>
- AIAgentFunctionalContext
- AIAgentConfig / AIAgentConfigBase
- PromptExecutor
- ToolRegistry
- FeatureContext and feature interfaces

See source: agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/FunctionalAIAgent.kt
