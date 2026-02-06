# Module agents-protocol

Declarative JSON-based protocol for defining and executing multi-agent workflows.

## Overview

The agents-protocol module provides a configuration-first approach to building complex agent workflows. Instead of writing code to wire agents together, you define flows in JSON that specify agents, tools, transitions, and execution logic. The module parses these configurations and executes them using the Koog framework.

Key capabilities:
- Define multi-agent workflows in JSON format
- Configure agents with different LLM models and parameters
- Create conditional transitions between agents based on outputs
- Integrate MCP (Model Context Protocol) tools via SSE or Stdio transports
- Execute complex workflow patterns (sequential, branching, loops, decision trees)

This enables rapid prototyping, dynamic workflow loading, and clear separation between workflow logic and implementation.

## Key Concepts

**Flow**: A complete workflow definition containing agents, tools, and transitions. Flows are parsed from JSON and executed by the Koog framework.

**Agents**: Processing units that can perform tasks (LLM-based), verify outputs, or transform data. Each agent has a name, type, optional model configuration, and parameters.

**Transitions**: Define the execution flow between agents, optionally with conditions based on agent outputs.

**Tools**: External capabilities (MCP or local) that agents can use during execution.

**Main Entry Point**: `FlowJsonConfigParser.parse()` converts JSON → `KoogFlow.run()` executes the workflow

## Using in your project

To use the agents-protocol module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-protocol:$version")
}
```

Then, you can create and execute flows by following these steps:
1. Define your workflow in JSON format (agents, tools, transitions)
2. Parse the JSON configuration using `FlowJsonConfigParser`
3. Create a `KoogFlow` instance with the parsed configuration
4. Execute the flow with initial input
5. Process the final output

See the [Usage](#usage) section below for code examples.

## Architecture

### Core Components

**FlowJsonConfigParser** — Parses JSON workflow definitions into typed configuration objects using kotlinx.serialization. Handles agent models, tool definitions, transitions, and condition serialization.

**KoogFlow** — Main execution orchestrator that builds the agent graph from configuration, initializes LLM executors and tool registries, and manages workflow execution lifecycle.

**FlowAgent** — Abstract representation of workflow nodes. Subtypes include:
- `FlowTaskAgent` - Executes LLM-based tasks with optional tool access
- `FlowVerifyAgent` - Validates outputs and returns `InputCritiqueResult` with success/failure
- `FlowInputTransformAgent` - Transforms inputs without LLM calls (e.g., extract fields)

**FlowTransition** — Defines edges between agents with optional conditions. Conditions evaluate agent outputs to determine routing.

**FlowTool** — External capabilities available to agents. Supports:
- MCP tools via SSE (HTTP Server-Sent Events) or Stdio (process-based) transports
- Local tools loaded by fully-qualified class name

**KoogStrategyFactory** — Builds graph-based execution strategies from flow configuration. Creates subgraph nodes for each agent and conditional edges from transitions.

**KoogPromptExecutorFactory** — Resolves model strings (e.g., `"openai/gpt-4o"`) to LLM clients for multiple providers (OpenAI, Anthropic, Google, Mistral, DeepSeek, OpenRouter, Ollama).

### Execution Pipeline

1. **Parse Phase**: JSON → `FlowConfig` via `FlowJsonConfigParser`
2. **Build Phase**: `KoogFlow` constructs:
   - Prompt executor from model configurations
   - Tool registry from MCP and local tool definitions
   - Graph strategy from agents and transitions
3. **Execution Phase**: `flow.run(input)` executes the graph
   - Starts at first agent
   - Evaluates conditions on transitions to select next agent
   - Continues until reaching `__finish__` or error
4. **Output Phase**: Returns final `FlowAgentInput` result

### Key Design Patterns

- **Configuration-as-Code**: Workflows defined declaratively in JSON, executed imperatively by Koog
- **Type-Safe Inputs/Outputs**: All agent inputs/outputs use sealed `FlowAgentInput` hierarchy for compile-time safety
- **Pluggable Agent Types**: New agent types can be added by extending `FlowAgent` and updating the parser
- **Condition DSL**: Declarative condition evaluation on typed inputs with extensible operations
- **Multi-Provider LLM Support**: Model resolution abstraction allows any agent to use any supported LLM

## Agent Types

| JSON `type`   | Behavior                                                                          |
|---------------|-----------------------------------------------------------------------------------|
| `"task"`      | Calls LLM with task from `params.task`, can use tools from `params.toolNames`     |
| `"verify"`    | Calls LLM for validation, outputs `InputCritiqueResult(success, feedback, input)` |
| `"transform"` | Transforms input without LLM (e.g., extract `success` from `InputCritiqueResult`) |
| `"parallel"`  | Not yet implemented                                                               |

## Input/Output Types (`FlowAgentInput`)

```kotlin
sealed interface FlowAgentInput {
    sealed interface Primitive : FlowAgentInput  // For condition evaluation

    // Primitives
    data class InputInt(val data: Int) : Primitive
    data class InputDouble(val data: Double) : Primitive
    data class InputString(val data: String) : Primitive
    data class InputBoolean(val data: Boolean) : Primitive

    // Arrays
    data class InputArrayInt(val data: Array<Int>)
    data class InputArrayDouble(val data: Array<Double>)
    data class InputArrayStrings(val data: Array<String>)
    data class InputArrayBooleans(val data: Array<Boolean>)

    // Special (verify agent output)
    data class InputCritiqueResult(
        val success: Boolean,
        val feedback: String,
        val input: FlowAgentInput
    )
}
```

## Tool Types (`FlowTool`)

```kotlin
sealed interface FlowTool {
    sealed interface Mcp : FlowTool {
        data class SSE(val url: String, val headers: Map<String, String>)  // HTTP SSE transport
        data class Stdio(val command: String, val args: List<String>)      // Command-line (JVM only)
    }
    data class Local(val fqn: String)  // Local tool by fully-qualified class name
}
```

## JSON Schema

### Root Structure
```json
{
  "id": "string",
  "version": "string",
  "description": "string",              // Optional: Flow description
  "defaultModel": "provider/model-id",  // Optional: e.g., "openai/gpt-4o"
  "tools": [...],
  "agents": [...],
  "transitions": [...]
}
```

### Agent
```json
{
  "name": "unique-agent-name",
  "type": "task|verify|transform",
  "model": "provider/model-id",         // Optional, falls back to defaultModel
  "runtime": "koog",                    // Optional: Currently only "koog" is implemented
  "config": {                           // Optional: LLM configuration
    "temperature": 0.7,
    "maxIterations": 10,
    "maxTokens": 4096,
    "topP": 0.9,
    "toolChoice": "auto|none|required|{\"toolName\":\"name\"}",
    "speculation": "string"
  },
  "prompt": {                           // Optional: Agent prompts
    "system": "System prompt text",
    "user": "User prompt text"          // Optional
  },
  "params": {                           // Agent-specific parameters
    "task": "Task description",         // For task/verify agents
    "toolNames": ["tool1", "tool2"],    // Optional: Restrict which tools agent can use
    "transformations": [...]            // For transform agents
  },
  "output": {                           // Optional: Output schema definition
    "schema": "json-schema-string"
  }
}
```

**Notes**:
- The `model` field in the `config` object in actual JSON examples appears to be deprecated/ignored. Use the top-level `model` field instead.
- Input is provided at runtime when calling `flow.run(input)`.

### Tool (MCP)
```json
{
  "name": "tool-name",
  "type": "mcp",
  "parameters": {
    "transport": "sse|stdio",
    "url": "http://...",                // For SSE
    "command": "npx",                   // For Stdio
    "args": ["-y", "@mcp/server"]       // For Stdio
  }
}
```

### Transition
```json
{
  "from": "source-agent-name",
  "to": "target-agent-name|__finish__",
  "condition": {                        // Optional
    "variable": "input.success",        // Field from FlowAgentInput
    "operation": "equals|not_equals|more|less|more_or_equal|less_or_equal|not|and|or",
    "value": true                       // Primitive value to compare against
  }
}
```

## Execution Flow

```
1. Parse JSON to FlowConfig
   - Deserializes JSON using kotlinx.serialization
   - Converts JSON models to internal representations

2. Create KoogFlow with configuration
   - Stores agents, tools, transitions, and default model

3. Execute flow with input
   ├── Build prompt executor for LLM calls
   │   - Resolves model strings to LLM clients per provider
   │
   ├── Build tool registry
   │   - Loads MCP tools via SSE or Stdio transport
   │   - Registers local tools by fully-qualified class name
   │
   ├── Build execution strategy
   │   - Creates graph with subgraph node for each agent
   │   - Creates edges from transitions with condition evaluation
   │
   └── Execute graph and return final output
```

## Condition Evaluation

Conditions in transitions allow routing based on agent outputs:
- Extract values from `FlowAgentInput` using paths like `"input.success"` or `"input.data"`
- Compare extracted values with condition values using operations
- Supported operations: `EQUALS`, `NOT_EQUALS`, `MORE`, `LESS`, `MORE_OR_EQUAL`, `LESS_OR_EQUAL`, `NOT`, `AND`, `OR`

## Supported LLM Providers

Model string format: `"provider/model-id"` (e.g., `"openai/gpt-4o"`, `"anthropic/claude-3-opus"`)

| Provider   | Environment Variable | Example Model             |
|------------|----------------------|---------------------------|
| OpenAI     | `OPENAI_API_KEY`     | `openai/gpt-4o`           |
| Anthropic  | `ANTHROPIC_API_KEY`  | `anthropic/claude-3-opus` |
| Google     | `GOOGLE_API_KEY`     | `google/gemini-pro`       |
| Mistral    | `MISTRAL_API_KEY`    | `mistral/mistral-large`   |
| DeepSeek   | `DEEPSEEK_API_KEY`   | `deepseek/deepseek-chat`  |
| OpenRouter | `OPENROUTER_API_KEY` | `openrouter/...`          |
| Ollama     | `OLLAMA_BASE_URL`    | `ollama/llama2`           |

## MCP Tool Integration

Tools defined with `"type": "mcp"` are loaded from MCP servers:
- **SSE**: HTTP Server-Sent Events transport for remote MCP servers
- **Stdio**: Process-based transport executing command with args (JVM only)

Tools from MCP servers are automatically discovered and registered. Agents can restrict available tools via `params.toolNames`.

## Dependencies

- `agents-core`: Core agent framework (graph agents, strategies, tool registry)
- `agents-mcp`: MCP tool integration
- `prompt-executor-llms-all`: Multi-provider LLM client support
- `kotlinx-serialization-json`: JSON parsing and serialization

## Usage

```kotlin
// Parse JSON configuration
val parser = FlowJsonConfigParser()
val config = parser.parse(jsonString)

// Create flow
val flow = KoogFlow(
    id = config.id ?: "my-flow",
    agents = config.agents,
    tools = config.tools,
    transitions = config.transitions,
    defaultModel = config.defaultModel
)

// Execute flow with initial input
val input = FlowAgentInput.InputString("Your task description here")
val result: FlowAgentInput = flow.run(input)
```

## Flow Patterns and Examples

This section describes common workflow patterns with complete JSON examples available in `src/jvmTest/resources/json/`.

### Basic Patterns

#### Pattern 1: Basic Task Flow
Simple sequential execution of task agents.

**Example**: `basic_task_flow.json` - Number generation → calculation

**Use cases**: Simple pipelines, data transformation chains

#### Pattern 2: Sequential Pipeline
Linear processing chain with multiple stages including verification.

**Example**: `sequential_pipeline_flow.json` - Data collection → enrichment → formatting → validation

**Use cases**: ETL pipelines, multi-stage transformations with quality checks

### Conditional Patterns

#### Pattern 3: Conditional Branching
Routes to different paths based on numeric or boolean conditions.

**Examples**:
- `conditional_branching_flow.json` - Score-based routing (high/medium/low)
- `multi_condition_routing_flow.json` - Boolean routing (safe/unsafe content)
- `string_comparison_flow.json` - String-based routing (language detection)

**Conditions used**: `MORE_OR_EQUAL`, `LESS`, `EQUALS`, `NOT_EQUALS`, `NOT`

**Use cases**: Content routing, priority handling, type-specific processing

### Loop Patterns

#### Pattern 4: Retry Loop
Iterative improvement with verify-fix-retry cycle.

**Example**: `retry_loop_flow.json` - Code generation with validation loop

**Key components**:
- Task agent generates output
- Verify agent validates (returns `InputCritiqueResult`)
- Transform agent extracts feedback
- Fixer agent corrects issues
- Loops until `success = true`

**Use cases**: Quality assurance, iterative refinement, validation workflows

#### Pattern 5: Verify-Transform
Simple verify-transform pattern for validation and feedback extraction.

**Example**: `verify_transform_flow.json` - Task execution with verification

**Use cases**: Single-pass validation, feedback handling

### Complex Patterns

#### Pattern 6: Decision Tree
Multiple branching points with convergence and nested loops.

**Example**: `complex_decision_tree_flow.json` - Document processing with classification, specialized handling, and archival

**Features**:
- 4-way classification branching
- Invoice path includes validation loop
- All paths converge to final archiver
- Combines branching, loops, and merge points

**Use cases**: Document processing, workflow orchestration, multi-stage routing

### Tool Integration

#### Pattern 7: MCP Tools
Integration with Model Context Protocol tools via SSE and Stdio transports.

**Example**: `greeting_flow_with_mcp_tool.json` - MCP tool usage with SSE and Stdio

**Use cases**: External API integration, tool-augmented agents

## Condition Operations Reference

All supported condition operations with examples:

| Operation       | Description           | Example Use Case                               | Example                                                                  |
|-----------------|-----------------------|------------------------------------------------|--------------------------------------------------------------------------|
| `EQUALS`        | Exact value match     | Route based on status, type, or boolean flag   | `{"variable": "input.data", "operation": "EQUALS", "value": "invoice"}`  |
| `NOT_EQUALS`    | Value mismatch        | Exclude specific values or types               | `{"variable": "input.data", "operation": "NOT_EQUALS", "value": "en"}`   |
| `MORE`          | Greater than          | Route high priority items                      | `{"variable": "input.data", "operation": "MORE", "value": 80}`           |
| `LESS`          | Less than             | Filter low scores or values                    | `{"variable": "input.data", "operation": "LESS", "value": 50}`           |
| `MORE_OR_EQUAL` | Greater than or equal | Threshold-based routing (≥ 80 = high)          | `{"variable": "input.data", "operation": "MORE_OR_EQUAL", "value": 80}`  |
| `LESS_OR_EQUAL` | Less than or equal    | Maximum value filtering                        | `{"variable": "input.data", "operation": "LESS_OR_EQUAL", "value": 100}` |
| `NOT`           | Boolean negation      | Invert boolean conditions                      | `{"variable": "input.data", "operation": "NOT", "value": true}`          |
| `AND`           | Logical AND           | Combine multiple boolean conditions            | `{"variable": "input.data", "operation": "AND", "value": true}`          |
| `OR`            | Logical OR            | Alternative boolean conditions                 | `{"variable": "input.data", "operation": "OR", "value": false}`          |

### Condition Evaluation Notes

1. **Type Compatibility**: For `EQUALS`/`NOT_EQUALS`, types must match exactly (e.g., `InputInt(42)` ≠ `InputDouble(42.0)`)
2. **Numeric Comparisons**: `MORE`, `LESS`, `MORE_OR_EQUAL`, `LESS_OR_EQUAL` work with `InputInt` and `InputDouble`
3. **String Comparisons**: String operations use case-insensitive lexicographic comparison
4. **InputCritiqueResult**: Access fields via `input.success` or `input.feedback` in condition variables
5. **Boolean Operations**: `NOT`, `AND`, `OR` only work with boolean values

## Complete Flow Examples

The following examples are available in `src/jvmTest/resources/json/`:

### 1. basic_task_flow.json
**Pattern**: Basic Task Flow
**Description**: Simple two-agent sequential flow with number generation and calculation.
**Key Features**: Demonstrates basic agent chaining and model override.

### 2. sequential_pipeline_flow.json
**Pattern**: Sequential Pipeline
**Description**: Four-stage pipeline with collection, enrichment, formatting, and verification.
**Key Features**: Linear processing with quality check at the end.

### 3. conditional_branching_flow.json
**Pattern**: Conditional Branching
**Description**: Score analyzer routing to high/medium/low feedback agents.
**Key Features**: Numeric comparisons with `MORE_OR_EQUAL` and `LESS` operations.

### 4. multi_condition_routing_flow.json
**Pattern**: Conditional Branching
**Description**: Content moderation routing safe vs unsafe content.
**Key Features**: Boolean conditions with `EQUALS` and `NOT` operations.

### 5. string_comparison_flow.json
**Pattern**: Conditional Branching
**Description**: Language detection routing to specialized processors.
**Key Features**: String comparison with `EQUALS` and `NOT_EQUALS` operations.

### 6. retry_loop_flow.json
**Pattern**: Retry Loop
**Description**: Code generation with verify-fix-retry cycle.
**Key Features**: `InputCritiqueResult`, transform agent for feedback extraction, loop until success.

### 7. verify_transform_flow.json
**Pattern**: Verify-Transform
**Description**: Task execution with verification and conditional fix path.
**Key Features**: Simple verify-transform pattern with feedback handling.

### 8. complex_decision_tree_flow.json
**Pattern**: Decision Tree
**Description**: Document processing with 4-way classification, specialized handlers, and convergence.
**Key Features**: Multiple branching points, nested validation loop, path convergence.

### 9. greeting_flow_with_mcp_tool.json
**Pattern**: MCP Tools
**Description**: MCP tool integration demonstrating both SSE and Stdio transports.
**Key Features**: Multiple MCP tool types, tool name restrictions via `toolNames`.

## Best Practices

### 1. Flow Design
- **Use descriptive agent names** - Name agents based on their function (e.g., `score_analyzer`, not `agent1`)
- **Add descriptions** - Document what each flow does at the top level using the `description` field
- **Handle all paths** - Ensure all conditional branches have valid targets
- **Avoid infinite loops** - Always have an exit condition in loops (use verify agents with success flags)
- **Converge paths when possible** - Multiple branches should merge to common final steps

### 2. Agent Configuration
- **Provide clear prompts** - System prompts should clearly explain the agent's role and output format
- **Use appropriate agent types**:
  - `task` for LLM-based processing
  - `verify` for validation that needs success/failure output
  - `transform` for extracting fields without LLM calls
- **Restrict tools when needed** - Use `params.toolNames` to limit which tools an agent can access

### 3. Conditions
- **Test edge cases** - Consider boundary values for numeric conditions
- **Use correct operations** - `MORE_OR_EQUAL` vs `MORE` for inclusive/exclusive bounds
- **Match types** - Ensure condition values match the expected output type
- **Use transforms wisely** - Extract only what you need from verify results

### 4. Error Handling
- **Validation loops** - Use verify agents to check quality before proceeding
- **Fallback paths** - Provide alternative routes for error cases
- **Timeouts** - Consider max iterations in loops to prevent infinite execution

### 5. Testing
Load and test flows in your test code:

```kotlin
val jsonContent = File("src/jvmTest/resources/conditional_branching_flow.json").readText()
val parser = FlowJsonConfigParser()
val flowConfig = parser.parse(jsonContent)

val flow = KoogFlow(
    id = flowConfig.id ?: "test-flow",
    agents = flowConfig.agents,
    tools = flowConfig.tools,
    transitions = flowConfig.transitions,
    defaultModel = flowConfig.defaultModel
)

val result = flow.run(FlowAgentInput.InputString("Test input"))
```

## Using in unit tests

The agents-protocol module is designed for easy testing:
- **Load flows from JSON resources** - Store test flows in `src/jvmTest/resources/json/` for easy access
- **Mock LLM responses** - Use agents-test utilities to mock LLM behavior for deterministic testing
- **Test condition evaluation** - Validate transition logic with different input types
- **Verify flow execution** - Assert final outputs match expected results for given inputs

Example test patterns:
```kotlin
@Test
fun testConditionalFlow() {
    val flowJson = File("src/jvmTest/resources/json/conditional_branching_flow.json").readText()
    val parser = FlowJsonConfigParser()
    val flowConfig = parser.parse(flowJson)

    val flow = KoogFlow(/* ... */)
    val result = flow.run(FlowAgentInput.InputInt(85))

    // Assert expected routing and output
    assertTrue(result is FlowAgentInput.InputString)
    assertContains((result as FlowAgentInput.InputString).data, "high score")
}
```

The module includes comprehensive tests demonstrating:
- Condition operation tests covering all comparison and logical operators
- Integration tests with mocked LLM responses
- JSON parsing and validation tests
- Test JSON examples in `src/jvmTest/resources/json/` demonstrating all workflow patterns
