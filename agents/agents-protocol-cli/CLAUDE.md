# agents-protocol-cli Module

## Module Overview

`agents-protocol-cli` is a **JVM-only command-line application** that lets users run any `agents-protocol` flow config file without writing Kotlin code. It is kept deliberately separate from `agents-protocol` (which is multiplatform, published to Maven, and uses `explicitApi()`).

### Purpose
- Provide a `flow <file.json>` command for executing flows from the terminal
- Support optional `--input` override and `--verbose` progress output
- Serve as a thin, dependency-free wrapper around `FlowJsonConfigParser` + `KoogFlow`

### Usage syntax

```
flow <file.json> [--input|-i <text>] [--verbose|-v] [--help|-h]
```

### Exit codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Flow execution error |
| 2 | Bad arguments or missing file |
| 3 | JSON parse error |

### Source layout

```
src/main/kotlin/ai/koog/protocol/cli/
├── Main.kt            Entry point — catches ExitException, calls exitProcess(code)
├── FlowCli.kt         Argument parsing and flow execution logic
├── ExitException.kt   Internal exception thrown instead of exitProcess() — enables testing
└── OutputFormatter.kt Formats FlowDataType sealed subclasses to printable strings
```

## Technologies & Dependencies

- **kotlin("jvm")** via the `ai.kotlin.jvm` convention plugin
- **application** Gradle plugin — produces a runnable distribution via `installDist`
- **`:agents:agents-protocol`** — `FlowJsonConfigParser`, `KoogFlow`, `FlowDataType`, `FlowConfig.toKoogFlow()`
- **kotlinx-coroutines-core** — `runBlocking` in `Main.kt`

## Development Guidelines

### Architecture decisions
⚠️ **Always ask before**:
- Adding new CLI flags or changing existing flag semantics
- Adding a dependency beyond `agents-protocol` and `kotlinx-coroutines-core`
- Changing exit code values (they are part of the CLI contract)

### Code style

#### Visibility
- Classes and objects are `internal` — this module is an application, not a library
- No `explicitApi()` enforced here; internal is the correct default

#### Argument parsing
- Manual parsing in `FlowCli` — no third-party CLI framework; keep it that way unless the number of flags grows significantly
- **Never call `exitProcess()` directly in `FlowCli`** — throw `ExitException(code)` instead. `Main.kt` is the only place that calls `exitProcess()`. This keeps `FlowCli` testable without terminating the JVM.
- Flow execution errors catch `Throwable` (not just `Exception`) — framework statics such as `KoogPromptExecutorFactory` can throw `ExceptionInInitializerError` in test environments where no API key is present.

#### Output
- **stdout** — final flow result only (keeps output pipeable)
- **stderr** — all error messages and `--verbose` progress lines

## Testing

### Running tests

```bash
# Run all tests
./gradlew :agents:agents-protocol-cli:jvmTest

# Run a specific test class
./gradlew :agents:agents-protocol-cli:jvmTest --tests "ai.koog.protocol.cli.OutputFormatterTest"
./gradlew :agents:agents-protocol-cli:jvmTest --tests "ai.koog.protocol.cli.FlowCliTest"
```

### Test structure

**`OutputFormatterTest`** — 14 unit tests, one per `FlowDataType` branch (all primitives, all array types including empty/single-element, `FlowCritiqueResult` success and failure, `ParallelExecutionResult` plain and with nested output).

**`FlowCliTest`** — 13 tests covering all argument-parsing and file-error paths without real LLM calls:
- `--help` / `-h` → exit 0; no args → exit 2 (both with help text on stdout)
- Unknown flag, multiple positional args, `--input`/`-i` without a value → exit 2 with specific error on stderr
- Non-existent file → exit 2; malformed JSON / wrong JSON type → exit 3
- `--verbose` / `-v` recognised (verified by reaching "file not found" rather than "unknown option")
- Verbose lines (`[flow] Agents: ...`, `[flow] Running flow: ...`) appear on stderr before execution

### Testing approach

`FlowCli` is tested by injecting `PrintStream` instances into the constructor and catching `ExitException`:

```kotlin
private val outBytes = ByteArrayOutputStream()
private val errBytes = ByteArrayOutputStream()
private fun cli() = FlowCli(stdout = PrintStream(outBytes), stderr = PrintStream(errBytes))

val ex = assertFailsWith<ExitException> { runBlocking { cli().run(arrayOf("--help")) } }
assertEquals(0, ex.code)
assertContains(outBytes.toString(Charsets.UTF_8), "Usage:")
```

⚠️ **Always** run `jvmTest` after any change to `FlowCli`, `OutputFormatter`, or `ExitException`.

## Common Commands

```bash
# Build the module
./gradlew :agents:agents-protocol-cli:build

# Run all tests
./gradlew :agents:agents-protocol-cli:jvmTest

# Run via Gradle (pass args after the space, inside quotes)
./gradlew :agents:agents-protocol-cli:run --args="--help"
./gradlew :agents:agents-protocol-cli:run --args="path/to/flow.json"
./gradlew :agents:agents-protocol-cli:run --args="path/to/flow.json -i 'My input' --verbose"

# Build a self-contained distribution
./gradlew :agents:agents-protocol-cli:installDist

# Run the installed binary directly
./agents/agents-protocol-cli/build/install/flow/bin/flow path/to/flow.json
./agents/agents-protocol-cli/build/install/flow/bin/flow path/to/flow.json -i "Hello" -v

# Compile only (faster iteration)
./gradlew :agents:agents-protocol-cli:compileKotlin
```
