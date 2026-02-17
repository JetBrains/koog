# agents-protocol Module

## Module Overview

The `agents-protocol` module provides a **declarative, JSON-based runtime for multi-agent workflows**. Instead of writing Kotlin to wire agents together, you define a flow in JSON — specifying agents, tools, and transitions — and the module parses and executes it using the Koog framework.

Full reference documentation (JSON schema, all agent types, all condition operations, flow examples): `Module.md` in this directory.

### Purpose
- Parse JSON flow configurations into typed Kotlin objects
- Execute multi-agent workflows without any user-written Kotlin
- Support sequential, branching, looping, parallel, and ReAct workflow patterns
- Integrate MCP tools (SSE and Stdio transports) and local Kotlin tools
- Enable rapid prototyping and dynamic workflow loading

### Architecture

**`FlowJsonConfigParser`** — Parses JSON → `FlowConfig` using kotlinx.serialization. Handles polymorphic agent types, custom `FlowDataType` serialization, and tool/transition model conversion.

**`KoogFlow`** — Main execution orchestrator. Builds a `GraphAIAgent` from `FlowConfig`, initialises the `PromptExecutor` and `ToolRegistry`, and runs the agent graph. Always installs `FlowEventHandler` (lifecycle logging cannot be disabled from outside the module).

**`KoogStrategyFactory`** — Converts `FlowConfig` into an `AIAgentGraphStrategy`. Each flow agent becomes a subgraph node; transitions become conditional or unconditional edges. Agents without outgoing transitions are automatically connected to `__finish__`.

**`KoogPromptExecutorFactory`** — Resolves `"provider/model-id"` strings (e.g. `"anthropic/sonnet_4"`, `"openai/gpt4o"`) to LLM clients by reading API keys from environment variables.

**`FlowValidator`** — Validates structural correctness (missing agents, cycles, unreachable nodes) before execution.

**`FlowDataType`** — Sealed interface for all agent I/O values. 12 concrete types: primitives (`FlowString`, `FlowInteger`, `FlowDouble`, `FlowBoolean`), arrays, `FlowCritiqueResult` (verify agent output), and `ParallelExecutionResult` (parallel agent child output).

### Execution pipeline

```
JSON string
  → FlowJsonConfigParser.parse()  →  FlowConfig
  → FlowConfig.toKoogFlow()       →  KoogFlow
  → KoogFlow.run(input?)          →  FlowDataType result
```

`run(null)` auto-derives input from the first agent's `params.task`. `run()` is a `suspend` function; wrap in `runBlocking` if calling from non-coroutine code.

### Source layout

```
src/commonMain/kotlin/ai/koog/protocol/
├── agent/          FlowDataType, FlowAgent base, FlowAgentConfig, ToolChoiceKind
│   └── agents/     Concrete agent types: task/, verify/, transform/, react/, parallel/
├── flow/           KoogFlow, FlowConfig (+toKoogFlow()), KoogStrategyFactory,
│                   KoogPromptExecutorFactory, FlowValidator, FlowUtil
├── parser/         FlowConfigJsonParser (main), FlowDataTypeSerializer, FlowToolKindSerializer
├── model/          JSON DTOs: FlowModel, FlowAgentModel, FlowToolModel, FlowTransitionModel
├── transition/     FlowTransition, FlowTransitionCondition
├── tool/           FlowTool (sealed: Mcp.SSE, Mcp.Stdio, Local)
└── feature/        FlowEventHandler (lifecycle logging, always installed)
```

## Technologies & Dependencies

- **Kotlin Multiplatform** (`commonMain` + `jvmMain` for Stdio MCP transport)
- **kotlinx.serialization** — JSON parsing with custom serializers for `FlowDataType` and `FlowToolKind`
- **agents-core** — `GraphAIAgent`, `AIAgentGraphStrategy`, `ToolRegistry`
- **agents-mcp** — MCP tool registry via SSE and Stdio transports
- **prompt-executor-llms-all** — multi-provider LLM client support

## Development Guidelines

### Architecture decisions
⚠️ **Always ask before** making any of these changes:
- Adding a new agent type (affects parser, strategy factory, and tests)
- Changing `FlowDataType` subtypes (serialization format is a public contract)
- Modifying condition evaluation semantics
- Changing how `KoogFlow` builds or executes the agent graph
- Adding new LLM providers to `KoogPromptExecutorFactory`

### Code style

#### API visibility
- `explicitApi()` is **enforced** — every public declaration needs an explicit visibility modifier
- Keep internal implementation classes `internal`; only types users need to call are `public`
- Model classes in `model/` are DTOs for the parser only — do not expose them beyond `parser/`

#### Naming
- Agent classes: `Flow<Type>Agent` (e.g. `FlowTaskAgent`, `FlowVerifyAgent`)
- Parameter classes: `Flow<Type>AgentParameters`
- Factory objects: `Koog<Thing>Factory`

#### Multiplatform
- All logic that can run on both JVM and JS goes in `commonMain`
- JVM-only code (Stdio process management) uses the `expect/actual` pattern in `jvmMain`
- Never add JVM-only dependencies in `commonMain`

#### KDoc
- All `public` declarations must have KDoc (enforced by `explicitApi()`)
- Document `@param`, `@return`, and `@throws` for non-trivial functions
- Use `[ClassName]` syntax for cross-references

## Testing

### Running tests

```bash
# Run all tests for this module
./gradlew :agents:agents-protocol:jvmTest

# Run a specific test class
./gradlew :agents:agents-protocol:jvmTest --tests "ai.koog.protocol.flow.FlowConditionTest"

# Run a specific test method
./gradlew :agents:agents-protocol:jvmTest --tests "ai.koog.protocol.parser.FlowConfigJsonParserTest.testBasicTaskFlow"

# Compile only (faster iteration)
./gradlew :agents:agents-protocol:compileKotlinJvm
```

### Testing approach
- **Unit tests**: condition evaluation, JSON parsing, `FlowValidator` logic — no LLM calls needed
- **Integration tests**: full `KoogFlow.run()` with mocked LLM responses via `agents-test`; require API keys for real-LLM tests and are skipped when keys are absent
- Test JSON flows live in `src/jvmTest/resources/json/`; add a new JSON file for each new agent type or pattern

### Loading test flows

```kotlin
val json = this::class.java.getResource("/json/basic_task_flow.json")!!.readText()
val config = FlowJsonConfigParser().parse(json)
val flow = config.toKoogFlow()
```

### Known serialization gotchas

⚠️ **`FlowModel.version` is required.** Despite appearing optional in the data class, kotlinx.serialization treats it as required. Any hand-written test JSON that omits `"version"` will produce:
```
Field 'version' is required for type with serial name 'ai.koog.protocol.model.FlowModel', but it was missing at path: $
```
Always include `"version": "1.0"` in test JSON files.

### Preferred assertion pattern

**✅ DO** — assert on the whole `FlowDataType` value:
```kotlin
assertEquals(FlowDataType.FlowString("42"), result)
assertEquals(FlowDataType.FlowCritiqueResult(success = true, feedback = "ok", input = input), result)
```

**❌ AVOID** — asserting individual fields unless the type has many fields:
```kotlin
assertTrue((result as FlowDataType.FlowCritiqueResult).success)
assertEquals("ok", (result as FlowDataType.FlowCritiqueResult).feedback)
```

### Test flow files

| File | Pattern demonstrated |
|------|----------------------|
| `basic_task_flow.json` | Sequential two-agent chain |
| `sequential_pipeline_flow.json` | Linear multi-stage pipeline |
| `conditional_branching_flow.json` | Numeric routing (high / medium / low) |
| `multi_condition_routing_flow.json` | Boolean routing |
| `string_comparison_flow.json` | String-based routing |
| `retry_loop_flow.json` | Verify → transform → fix loop |
| `verify_transform_flow.json` | Single-pass verify + feedback extract |
| `complex_decision_tree_flow.json` | 4-way branch with nested validation loop |
| `parallel_flow.json` | Concurrent child agents |
| `react_flow.json` | ReAct strategy with tool use |
| `greeting_flow_with_mcp_tool.json` | MCP tools (SSE + Stdio) |

## Implementation Patterns

### Adding a new agent type

1. Add an enum value to `FlowAgentKind`
2. Create `FlowXxxAgentParameters : FlowAgentParameters` annotated with `@Serializable`
3. Create `FlowXxxAgent : FlowAgent` data class
4. Add a deserialization branch in `FlowConfigJsonParser` (the `when (agentModel.type)` block)
5. Add an execution branch in `KoogStrategyFactory` (the `buildAgentSubgraph()` function)
6. Add a test JSON file in `src/jvmTest/resources/json/` and a corresponding test

### Adding a new condition operation

1. Add the value to `ConditionOperationKind`
2. Add evaluation logic in `FlowTransitionCondition` (the `evaluate()` function)
3. Add test coverage in `FlowConditionTest`

### Adding a new LLM provider

Add resolution logic in `KoogPromptExecutorFactory.resolveModel()` following the existing `when` pattern. Read the API key from an environment variable; document the variable name in the provider table in `Module.md`.

## Common Commands

```bash
# Build the module
./gradlew :agents:agents-protocol:build

# Run all JVM tests
./gradlew :agents:agents-protocol:jvmTest

# Run a specific test class
./gradlew :agents:agents-protocol:jvmTest --tests "ai.koog.protocol.flow.FlowConditionTest"

# Compile only (no tests, faster)
./gradlew :agents:agents-protocol:compileKotlinJvm

# Build without running tests
./gradlew :agents:agents-protocol:assemble
```
