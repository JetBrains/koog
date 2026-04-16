# Contributing Guidelines

One can contribute to the project by reporting issues or submitting changes via pull request.

## Reporting issues

Please use [Koog official YouTrack project](https://youtrack.jetbrains.com/issues/KG) for filing feature
requests and bug reports.

Questions about usage and general inquiries are better suited for the [#koog-agentic-framework](https://kotlinlang.slack.com/messages/koog-agentic-framework/) channel in KotlinLang Slack.

## Submitting changes

### General guidelines

Submit pull requests [here](https://github.com/JetBrains/koog/pulls).
However, please keep in mind that maintainers will have to support the resulting code of the project,
so do familiarize yourself with the following guidelines:

* All development (both new features and bug fixes) is performed in the `develop` branch. Base your PRs against it.
* If you make any code changes:
    * Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/reference/coding-conventions.html).
    * [Build the project](#building) to make sure it all works and passes the tests.
* If you fix a bug:
    * Write the test that reproduces the bug.
    * Fixes without tests are accepted only in exceptional circumstances if it can be shown that writing the
      corresponding test is too hard or otherwise impractical.
    * Follow the style of writing tests that is used in this project:
      name test functions as `testXxx`. Don't use backticks in test names.
* Comment on the existing issue if you want to work on it. Ensure that the issue not only describes a problem but also describes a solution that has received positive feedback. Propose a solution if none has been suggested.

### Conventional PRs

This project uses the "Squash & Merge" strategy, which turns each PR into a single commit when merged into `develop`.
Because of this, we follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) for PR titles and descriptions.

**For breaking changes**, append `!` after the type/scope: `refactor(agents)!: remove deprecated methods from Tool`

#### Supported types

- `feat` — New feature
- `fix` — Bug fix
- `docs` — Documentation changes
- `refactor` — Code refactoring (no functional changes)
- `test` — Adding or updating tests
- `build` — Build system or dependency changes
- `ci` — CI/CD configuration changes
- `example` — Changes to examples

#### Supported scopes (optional)

You don't have to specify a scope for PRs that affect multiple scopes, but it's recommended to set scope(s) when possible
for greater clarity. Currently, scopes are based on the top-level modules of the project. This might change in the future.
The following scopes are supported:

- `a2a`
- `agents`
- `embeddings`
- `integration-tests`
- `koog-agents`
- `koog-ktor`
- `spring-boot`
- `prompt`
- `rag`
- `test-utils`
- `utils`
- `serialization`

#### Description

* Always provide a clear description of the changes you're making and why you're making them.
* Include "BREAKING:" section if the PR introduces breaking changes (incompatible API changes), e.g.:
```
BREAKING:
* Tool.execute() now requires an additional parameter
* Removed deprecated Message.metadata property
```
* Include "DEPRECATED:" section if the PR deprecates any public APIs, e.g.:
```
DEPRECATED:
* Tool.foo method in favor of Tool.bar
* Message.baz property
```
* Use "closes" keyword to link the PR to the issue(s) it addresses, e.g., "closes #1, closes KG-1".

#### Examples

The following are examples of properly formatted PR titles and descriptions:

**New feature:**
```
feat(agents): add support for streaming responses


Add streaming response support to the agents module, allowing real-time
token-by-token processing of LLM outputs.

closes #123, closes KG-456
```

**PR with deprecations:**
```
refactor(prompt): simplify PromptExecutor interface by deprecating redundant methods


Refactors the PromptExecutor interface to deprecate redundant methods and improve
type safety for structured outputs.

DEPRECATED:
* PromptExecutor.executeWithSchema() in favor of PromptExecutor.execute() with structured parameter
* Message.metadata property

closes KG-789
```

**PR with breaking changes:**
```
feat(agents)!: redesign Tool interface for better type safety


Redesigns the Tool interface to enforce stricter type safety and improve
error handling. This is a breaking change that requires migration.

BREAKING:
* Tool.execute() now returns Result<T> instead of T
* ToolRegistry.register() requires explicit error handler
* Removed Tool.executeUnsafe() method

Migration guide: https://docs.koog.ai/migration/tool-interface-v2

closes KG-890
```

## Working with AI Code Agents

This project includes some helpful guidelines to make AI coding assistants work better with the codebase. 

### Agent Guidelines

You'll find an [AGENTS.md](AGENTS.md) file in the repository root.
Think of it as a cheat sheet for AI assistants that explains:

- **How the project works** — the overall architecture and main concepts
- **Development workflow** — which commands to run and how to build things
- **Testing patterns** — our approach to mocks and test structure
- **Code conventions** — the style we follow and why

### How to use `AGENTS.md`

When you're pairing with an AI assistant on this project:

1. Share the `AGENTS.md` file with your code agent of choice (Junie, Claude Code, Cursor, Copilot, etc.)
2. The AI will understand our project structure and conventions better
3. You can even use it as a starting point to create custom configs for specific agents

## Documentation

The documentation is published on https://docs.koog.ai/, and its sources are located in the
[docs](https://github.com/JetBrains/koog/tree/develop/docs) directory.

## Building

### Prerequisites

Koog is a Kotlin Multiplatform framework, and you need a bunch of tools to build it:

- JDK 17+ (21 is recommended)
- [Node.js](https://nodejs.org/en/download) installed, as it is required to build Kotlin/JS targets.
- [Android SDK](https://developer.android.com/tools) installed, as it is required for Android target.
- On macOS, you would need [Xcode / Command Line Tools](https://developer.apple.com/xcode/resources/), these are needed to build Native targets. 

### How to build

This project is built with Gradle.

* Run `./gradlew build` to build. It also runs all the tests.
* Run `./gradlew <module>:check` to test the module you are looking and to speed
  things up during development.

You can import this project into IDEA, but you have to delegate build actions
to Gradle (in Preferences -> Build, Execution, Deployment -> Build Tools -> Gradle -> Build and run).

## Running tests

Please find more information in the [TESTING.md](TESTING.md).
