# Module agents-protocol

Declarative JSON-based protocol for defining and executing multi-agent workflows in the Koog framework.

## Summary

- **Purpose**: Parse JSON flow definitions into executable Koog agent graphs
- **Main entry point**: `FlowJsonConfigParser` parses JSON → `KoogFlow` executes the workflow
- **Core pattern**: JSON describes agents and transitions → converted to Koog `GraphAIAgent` with `AIAgentGraphStrategy`

## Key Classes Reference

| Class                       | File                                    | Purpose                                                                             |
|-----------------------------|-----------------------------------------|-------------------------------------------------------------------------------------|
| `Flow`                      | `flow/Flow.kt`                          | Interface for executable workflows with `run()` method                              |
| `KoogFlow`                  | `flow/KoogFlow.kt`                      | Main implementation: builds `GraphAIAgent` from agents/transitions/tools            |
| `FlowJsonConfigParser`      | `parser/FlowConfigJsonParser.kt`        | Parses JSON string into `FlowConfig`                                                |
| `FlowConfig`                | `flow/FlowConfig.kt`                    | Data class holding parsed flow configuration                                        |
| `KoogStrategyFactory`       | `flow/KoogStrategyFactory.kt`           | Converts `FlowAgent` list + transitions to `AIAgentGraphStrategy`                   |
| `KoogPromptExecutorFactory` | `flow/KoogPromptExecutorFactory.kt`     | Creates `PromptExecutor` from model strings (e.g., `"openai/gpt-4o"`)               |
| `FlowAgent`                 | `agent/FlowAgent.kt`                    | Interface for flow agents with `name`, `type`, `model`, `config`, `prompt`          |
| `FlowAgentInput`            | `agent/FlowAgentInput.kt`               | Sealed interface for typed input/output values                                      |
| `FlowTool`                  | `tool/FlowTool.kt`                      | Sealed interface for tool definitions (MCP or Local)                                |
| `FlowTransition`            | `transition/FlowTransition.kt`          | Edge definition: `from` agent → `to` agent with optional condition                  |
| `FlowTransitionCondition`   | `transition/FlowTransitionCondition.kt` | Condition for conditional transitions                                               |

## Package Structure

```
ai.koog.protocol/
├── agent/                      # Agent abstractions
│   ├── FlowAgent.kt            # Interface: name, type, model, config, prompt, parameters
│   ├── FlowAgentInput.kt       # Sealed interface for typed I/O (primitives, arrays, critique result)
│   ├── FlowAgentKind.kt        # Enum: TASK, VERIFY, TRANSFORM, PARALLEL
│   ├── FlowAgentConfig.kt      # Config: temperature, maxIterations, maxTokens, topP, toolChoice
│   ├── FlowAgentPrompt.kt      # Prompt: system, user
│   ├── FlowAgentRuntimeKind.kt # Enum: KOOG, LANG_CHAIN, CLAUDE_CODE, CODEX
│   └── agents/
│       ├── task/FlowTaskAgent.kt           # LLM task execution with tools
│       ├── verify/FlowVerifyAgent.kt       # LLM validation returning InputCritiqueResult
│       └── transform/FlowInputTransformAgent.kt  # Data transformation without LLM
├── flow/
│   ├── Flow.kt                 # Interface with run(input: FlowAgentInput): FlowAgentInput
│   ├── KoogFlow.kt             # Implementation: buildAgent() → GraphAIAgent
│   ├── FlowConfig.kt           # Parsed config data class
│   ├── FlowUtil.kt             # Helper: getFirstAgent() from transitions
│   ├── KoogStrategyFactory.kt  # Builds AIAgentGraphStrategy from agents/transitions
│   ├── KoogPromptExecutorFactory.kt  # Creates MultiLLMPromptExecutor from model strings
│   └── ConditionOperationKind.kt     # Enum: EQUALS, NOT_EQUALS, MORE, LESS, etc.
├── model/                      # JSON serialization models (kotlinx.serialization)
│   ├── FlowModel.kt            # Root: id, version, defaultModel, agents, tools, transitions
│   ├── FlowAgentModel.kt       # Agent JSON model with params as JsonObject
│   ├── FlowToolModel.kt        # Tool JSON model with custom serializer
│   └── FlowTransitionModel.kt  # Transition JSON model
├── parser/
│   ├── FlowConfigParser.kt     # Interface: parse(input: String): FlowConfig
│   └── FlowConfigJsonParser.kt # Implementation using kotlinx.serialization.json
├── tool/
│   └── FlowTool.kt             # Sealed: Mcp.SSE, Mcp.Stdio, Local
└── transition/
    ├── FlowTransition.kt       # Data class: from, to, condition
    └── FlowTransitionCondition.kt  # Data class: variable, operation, value
```

## Agent Types

| JSON `type`   | Class                     | Behavior                                                                          |
|---------------|---------------------------|-----------------------------------------------------------------------------------|
| `"task"`      | `FlowTaskAgent`           | Calls LLM with task from `params.task`, can use tools from `params.toolNames`     |
| `"verify"`    | `FlowVerifyAgent`         | Calls LLM for validation, outputs `InputCritiqueResult(success, feedback, input)` |
| `"transform"` | `FlowInputTransformAgent` | Transforms input without LLM (e.g., extract `success` from `InputCritiqueResult`) |
| `"parallel"`  | —                         | Not yet implemented                                                               |

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
  "defaultModel": "provider/model-id",  // e.g., "openai/gpt-4o"
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
  "runtime": "koog",                    // Currently only "koog" is implemented
  "config": {
    "temperature": 0.7,
    "maxIterations": 10,
    "maxTokens": 4096,
    "topP": 0.9
  },
  "prompt": {
    "system": "System prompt text"
  },
  "params": {
    "task": "Task description",
    "toolNames": ["tool1", "tool2"]     // Restrict which tools agent can use
  }
}
```

**Note**: The `input` field is not part of the JSON configuration. Input is provided at runtime when calling `flow.run(input)`.

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
1. FlowJsonConfigParser.parse(jsonString) → FlowConfig
   - Deserializes JSON using kotlinx.serialization
   - Converts FlowAgentModel → FlowAgent implementations

2. KoogFlow(id, agents, tools, transitions, defaultModel)
   - Stores flow configuration

3. KoogFlow.run(input: FlowAgentInput)
   ├── buildPromptExecutor() → MultiLLMPromptExecutor
   │   - KoogPromptExecutorFactory.resolveModel() for each agent's model
   │   - Creates LLMClient per provider (OpenAI, Anthropic, etc.)
   │
   ├── buildToolRegistry() → ToolRegistry
   │   - For MCP tools: McpToolRegistryProvider.fromTransport()
   │   - Merges all tool registries with + operator
   │
   ├── buildStrategy() → AIAgentGraphStrategy
   │   - KoogStrategyFactory.buildStrategy()
   │   - Creates subgraph node for each FlowAgent
   │   - Creates edges from transitions with condition evaluation
   │
   └── GraphAIAgent.run(input) → FlowAgentInput
       - Executes the strategy graph with provided input
       - Returns output from final agent
```

## Condition Evaluation

In `KoogStrategyFactory.evaluateCondition()`:
- Extracts value from `FlowAgentInput` based on `condition.variable` (e.g., `"input.success"`)
- Compares with `condition.value` using `condition.operation`
- Supports: `EQUALS`, `NOT_EQUALS`, `MORE`, `LESS`, `MORE_OR_EQUAL`, `LESS_OR_EQUAL`, `NOT`, `AND`, `OR`

## Supported LLM Providers

Model string format: `"provider/model-id"` (e.g., `"openai/gpt-4o"`, `"anthropic/claude-3-opus"`)

| Provider | Environment Variable | Example Model |
|----------|---------------------|---------------|
| OpenAI | `OPENAI_API_KEY` | `openai/gpt-4o` |
| Anthropic | `ANTHROPIC_API_KEY` | `anthropic/claude-3-opus` |
| Google | `GOOGLE_API_KEY` | `google/gemini-pro` |
| Mistral | `MISTRAL_API_KEY` | `mistral/mistral-large` |
| DeepSeek | `DEEPSEEK_API_KEY` | `deepseek/deepseek-chat` |
| OpenRouter | `OPENROUTER_API_KEY` | `openrouter/...` |
| Ollama | `OLLAMA_BASE_URL` | `ollama/llama2` |

## MCP Tool Integration

Tools defined with `"type": "mcp"` are loaded via `McpToolRegistryProvider`:
- **SSE**: `McpToolRegistryProvider.defaultSseTransport(url)` creates HTTP SSE connection
- **Stdio**: Creates process executing command with args (JVM only)

Tools from MCP servers are automatically discovered and added to `ToolRegistry`. Agents can restrict available tools via `params.toolNames`.

## Dependencies

- `agents-core`: `GraphAIAgent`, `AIAgentGraphStrategy`, `ToolRegistry`
- `agents-mcp`: `McpToolRegistryProvider` for MCP tool loading
- `prompt-executor-llms-all`: Multi-provider LLM client support
- `kotlinx-serialization-json`: JSON parsing

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

This section describes common workflow patterns with complete JSON examples available in `src/jvmTest/resources/`.

### Pattern 1: Sequential Pipeline

Linear processing chain without conditions. Each agent processes and passes to the next in order.

```
Agent1 → Agent2 → Agent3 → Finish
```

**Example**: `sequential_pipeline_flow.json` - Data collection → enrichment → formatting → validation

**Use cases**: ETL pipelines, data processing chains, multi-stage transformations

### Pattern 2: Conditional Branching

One agent routes to multiple paths based on conditions. All branches typically converge to a common endpoint.

```
           ┌→ AgentA → Finish
Analyzer ──┼→ AgentB → Finish
           └→ AgentC → Finish
```

**Example**: `conditional_branching_flow.json` - Score analysis with high/medium/low feedback paths

**Conditions used**: `MORE_OR_EQUAL`, `LESS` for numeric thresholds

**Use cases**: Content routing, priority-based processing, category-specific handling

### Pattern 3: Retry Loop

Iterative improvement pattern with verification and correction. Loops until verification succeeds.

```
Generator → Verifier ──success→ Finish
               ↓
            Fixer ←──failure──┘
```

**Example**: `retry_loop_flow.json` - Code generation with verify-fix-retry cycle

**Key agents**:
- Task agent generates initial output
- Verify agent checks quality (returns `InputCritiqueResult`)
- Transform agent extracts feedback
- Fixer agent corrects issues
- Loop continues until `success = true`

**Use cases**: Quality assurance workflows, iterative refinement, validation loops

### Pattern 4: Decision Tree

Multiple branching points with different paths that may converge. Complex workflows with classification and routing.

```
Classifier ──invoice→ InvoiceProcessor → Validator ──success→ Archive
    ├──contract→ ContractProcessor → RiskAnalyzer → Archive
    ├──report→ ReportProcessor → Archive
    └──other→ GenericProcessor → Archive
```

**Example**: `complex_decision_tree_flow.json` - Document processing system

**Features**:
- Initial classification with 4-way branching
- Invoice path includes validation loop
- All paths converge to final archiver
- Combines branching, loops, and merge points

**Use cases**: Document processing, workflow orchestration, multi-stage routing

### Pattern 5: String-Based Routing

Routes based on string comparison for type-specific processing.

**Example**: `string_comparison_flow.json` - Language detection routing to specialized processors

**Conditions used**: `EQUALS`, `NOT_EQUALS` on string values

**Use cases**: Language routing, content-type handling, category-based processing

### Pattern 6: Boolean Logic Routing

Simple binary decisions using boolean conditions.

**Example**: `multi_condition_routing_flow.json` - Content moderation (safe vs unsafe)

**Conditions used**: `EQUALS`, `NOT` on boolean values

**Use cases**: Content moderation, approval workflows, binary classification

## Condition Operations Reference

All supported condition operations with examples:

| Operation | Description | Example Use Case | Example |
|-----------|-------------|------------------|---------|
| `EQUALS` | Exact value match | Route based on status, type, or boolean flag | `{"variable": "input.data", "operation": "EQUALS", "value": "invoice"}` |
| `NOT_EQUALS` | Value mismatch | Exclude specific values or types | `{"variable": "input.data", "operation": "NOT_EQUALS", "value": "en"}` |
| `MORE` | Greater than | Route high priority items | `{"variable": "input.data", "operation": "MORE", "value": 80}` |
| `LESS` | Less than | Filter low scores or values | `{"variable": "input.data", "operation": "LESS", "value": 50}` |
| `MORE_OR_EQUAL` | Greater than or equal | Threshold-based routing (≥ 80 = high) | `{"variable": "input.data", "operation": "MORE_OR_EQUAL", "value": 80}` |
| `LESS_OR_EQUAL` | Less than or equal | Maximum value filtering | `{"variable": "input.data", "operation": "LESS_OR_EQUAL", "value": 100}` |
| `NOT` | Boolean negation | Invert boolean conditions | `{"variable": "input.data", "operation": "NOT", "value": true}` |
| `AND` | Logical AND | Combine multiple boolean conditions | `{"variable": "input.data", "operation": "AND", "value": true}` |
| `OR` | Logical OR | Alternative boolean conditions | `{"variable": "input.data", "operation": "OR", "value": false}` |

### Condition Evaluation Notes

1. **Type Compatibility**: For `EQUALS`/`NOT_EQUALS`, types must match exactly (e.g., `InputInt(42)` ≠ `InputDouble(42.0)`)
2. **Numeric Comparisons**: `MORE`, `LESS`, `MORE_OR_EQUAL`, `LESS_OR_EQUAL` work with `InputInt` and `InputDouble`
3. **String Comparisons**: String operations use case-insensitive lexicographic comparison
4. **InputCritiqueResult**: Access fields via `input.success` or `input.feedback` in condition variables
5. **Boolean Operations**: `NOT`, `AND`, `OR` only work with boolean values

## Complete Flow Examples

The following complete examples are available in `src/jvmTest/resources/`:

### 1. conditional_branching_flow.json
Score-based routing with three feedback paths based on numeric thresholds.

```json
{
  "id": "conditional-branching-flow",
  "defaultModel": "openai/gpt-4o",
  "agents": [
    {"name": "score_analyzer", "type": "task", ...},
    {"name": "high_score_feedback", "type": "task", ...},
    {"name": "medium_score_feedback", "type": "task", ...},
    {"name": "low_score_feedback", "type": "task", ...}
  ],
  "transitions": [
    {"from": "score_analyzer", "to": "high_score_feedback",
     "condition": {"variable": "input.data", "operation": "MORE_OR_EQUAL", "value": 80}},
    {"from": "score_analyzer", "to": "medium_score_feedback",
     "condition": {"variable": "input.data", "operation": "MORE_OR_EQUAL", "value": 50}},
    {"from": "score_analyzer", "to": "low_score_feedback",
     "condition": {"variable": "input.data", "operation": "LESS", "value": 50}}
  ]
}
```

### 2. retry_loop_flow.json
Verify-fix-retry pattern for iterative code generation.

```json
{
  "id": "retry-loop-flow",
  "defaultModel": "openai/gpt-4o",
  "agents": [
    {"name": "initial_generator", "type": "task", ...},
    {"name": "code_verifier", "type": "verify", ...},
    {"name": "extract_feedback", "type": "transform", ...},
    {"name": "code_fixer", "type": "task", ...}
  ],
  "transitions": [
    {"from": "initial_generator", "to": "code_verifier"},
    {"from": "code_verifier", "to": "__finish__",
     "condition": {"variable": "input.success", "operation": "EQUALS", "value": true}},
    {"from": "code_verifier", "to": "extract_feedback",
     "condition": {"variable": "input.success", "operation": "EQUALS", "value": false}},
    {"from": "extract_feedback", "to": "code_fixer"},
    {"from": "code_fixer", "to": "code_verifier"}
  ]
}
```

### 3. string_comparison_flow.json
Language detection with routing to specialized processors.

### 4. sequential_pipeline_flow.json
Simple data pipeline: collect → enrich → format → verify.

### 5. complex_decision_tree_flow.json
Document processing with classification, specialized handling, and convergence.

### 6. multi_condition_routing_flow.json
Content moderation with boolean routing.

### 7. verify_transform_flow.json
Task execution with verification and feedback handling.

### 8. random_koog_agent_flow.json
Simple sequential flow demonstrating multiple models.

### 9. greeting_flow_with_mcp_tool.json
MCP tool integration example.

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

## Testing

Comprehensive tests are available:
- **FlowTransitionConditionTest.kt** - 44 tests covering all condition operations
- **FlowExecutionTest.kt** - Integration tests with mocked LLM responses
- **FlowJsonParserTest.kt** - JSON parsing and validation tests
