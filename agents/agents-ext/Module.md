# Module agents-ext

Extends `agents-core` module with tools, as well as utilities for building graphs and strategies.

## AI agent CLI delegation

`AIAgentCliTool` lets a Koog agent delegate a bounded task to an installed AI coding CLI.
The integration is profile based: the host application owns the trusted command shape, while
tool calls provide only the task, optional context, working directory, and timeout.

Preset profiles are available for:

- OpenAI Codex CLI: `AIAgentCliProfiles.codex()`
- Anthropic Claude Code CLI: `AIAgentCliProfiles.claudeCode()`
- GitHub Copilot CLI: `AIAgentCliProfiles.githubCopilot()`

Custom CLI integrations can be added with `AIAgentCliProfiles.custom(...)`.

```kotlin
val tool = AIAgentCliTool(
    profile = AIAgentCliProfiles.codex(extraArguments = listOf("--full-auto")),
    executor = ShellAIAgentCliExecutor(shellCommandExecutor),
    confirmationHandler = PrintAIAgentCliConfirmationHandler()
)
```

`ShellAIAgentCliExecutor` is implemented in `commonMain` and adapts CLI profiles to Koog's
`ShellCommandExecutor` abstraction. JVM applications can pass `JvmShellCommandExecutor`; other
targets can supply their own shell executor implementation.
Use a confirmation handler unless the application already runs the CLI inside an appropriate sandbox.
