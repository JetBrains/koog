# Module agents-cli

Provides integration with external CLI-based AI agents (Claude Code, OpenAI Codex) by wrapping their command-line interfaces into the Koog agent framework.
The main components are:
- [**CliAIAgent**](src/commonMain/kotlin/ai/koog/agents/cli/CliAIAgent.kt): Factory for creating CLI-backed agents with `claude()` and `codex()` constructors
- [**CliTransport**](src/commonMain/kotlin/ai/koog/agents/cli/transport/CliTransport.kt): Abstraction for executing CLI commands via local process or Docker
- [**CliAIAgentResponse**](src/commonMain/kotlin/ai/koog/agents/cli/CliAIAgentResponse.kt): Response model containing content, error status, and token usage metadata

## Overview

The `agents-cli` module allows you to use Claude Code and OpenAI Codex as agents within Koog workflows. Instead of communicating with LLMs through API calls, it invokes their CLI binaries and parses the output. Agents can run locally via `ProcessCliTransport` or inside Docker containers via `DockerCliTransport`.

CLI agents can also be embedded as nodes in Koog graph-based strategies using `asNode()`, enabling multi-agent orchestration.

## Usage

### Basic Claude Agent

```kotlin
val agent = CliAIAgent.claude(
    transport = CliTransport.default(),
    apiKey = "your-anthropic-key",
    systemPrompt = "Follow user instructions without asking for confirmations."
)

val response = agent.run("echo 'hello'")
println(response.content)    // Agent output
println(response.isError)    // false on success
```

### Basic Codex Agent

```kotlin
val agent = CliAIAgent.codex(
    transport = CliTransport.default(),
    apiKey = "your-openai-key",
    systemPrompt = "Follow user instructions."
)

val response = agent.run("echo 'hello'")
```

### Structured Output

```kotlin
@Serializable
data class StructuredResult(val message: String)

val agent = CliAIAgent.claude<String, StructuredResult>(
    transport = CliTransport.default(),
    apiKey = "your-anthropic-key",
    systemPrompt = "Follow user instructions.",
)

val result = agent.run("echo 'hi'")
println(result.structuredResult) // Parsed StructuredResult
println(result.response.content) // Raw response
```

### Docker Transport

```kotlin
val agent = CliAIAgent.claude(
    transport = DockerCliTransport("cli-agents"),
    apiKey = "your-anthropic-key",
    systemPrompt = "Follow user instructions."
)

val response = agent.run("echo 'hello'")
```

### Composing CLI Agents in a Graph

```kotlin
val claude = CliAIAgent.claude(
    transport = CliTransport.default(),
    apiKey = claudeApiKey,
    systemPrompt = "Generate a plan.",
)

val codex = CliAIAgent.codex<CliAIAgentResponse>(
    transport = CliTransport.default(),
    apiKey = codexApiKey,
    systemPrompt = "Execute the plan.",
    generateRequest = { "echo '${it.content}'" }
)

val strategy = strategy("multi-agent") {
    val generatePlan by claude.asNode()
    val solveTask by codex.asNode()

    nodeStart then generatePlan then solveTask then nodeFinish
}

val agent = AIAgent(
    promptExecutor = executor,
    agentConfig = agentConfig,
    strategy = strategy,
)

agent.run("echo 'hi'")
```
