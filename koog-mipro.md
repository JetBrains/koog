# Koog MIPRO Integration - Design Document

This document describes the integration of MIPRO v2 prompt optimization into Koog's native abstractions.

## Goal

Support automatic prompt optimization (MIPRO v2) within Koog's agent framework. Users define `OptimizableNode`s — nodes whose instruction and demonstrations can be tuned by an optimizer — alongside regular nodes. Optimizers discover these nodes, evaluate candidate configurations, and return an `OptimizationConfig` that can be applied via coroutine context or baked into node defaults.

## Reference Materials

- **MIPRO paper:** `koog-auto-agent-optimization/MIPRO.pdf`
- **Current MIPRO implementation:** `koog-auto-agent-optimization/src/main/kotlin/promptOptimization/`
- **Koog abstractions summary:** `koog-auto-agent-optimization/koog-abstractions.md`
- **Koog source:** `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/`

## Architecture

### Key Design Decisions

1. **Separate type, don't pollute base.** Optimization fields live on a dedicated `OptimizableNode` subclass of `AIAgentNode`, not on the base class. Regular nodes are unaffected. This was a deliberate reversal of an earlier "extend existing types" approach — adding optional fields to `AIAgentNode` created coupling between the optimization package and the core agent API.

2. **Node owns its prompt construction.** Each `OptimizableNode` carries a `promptFn` that builds a `Prompt` from (instruction, demonstrations, input). The node's execute lambda calls `promptFn`, then passes the result to an internal `executePrompt` function that handles the LLM call and response parsing. Users never write boilerplate to read from coroutine context.

3. **Configuration via coroutine context.** During optimization, candidate configurations (instruction overrides, demonstration sets) are passed through `OptimizationConfig` in the coroutine context. Nodes check the context first, fall back to their own defaults. This enables parallel evaluation without mutating nodes or copying strategy graphs.

4. **Pipeline feature for trace collection.** `TraceCollectionFeature` uses Koog's pipeline mechanism to capture node inputs/outputs during execution, for use in bootstrapping.

5. **Leverage Koog's type system.** Nodes are already generic with `KType`. Structured output uses Koog's `executeStructured<T>()` for typed response parsing.

### Concept Mapping

| MIPRO Concept | Koog Integration |
|---------------|------------------|
| `Agent` | `AIAgentGraphStrategy` (unchanged) |
| `AgentModule` | `OptimizableNode<TInput, TOutput>` (subclass of `AIAgentNode`) |
| `Signature.instruction` | `OptimizableNode.instruction: String` |
| `Trace` | `Demonstration<TInput, TOutput>` |
| `Example` | `Example(data: Map<String, Any>, labelKey)` |
| `Optimizer` | `StrategyOptimizer` interface |

### OptimizableNode

`OptimizableNode<TInput, TOutput>` is a subclass of `AIAgentNode` that owns all optimization-related state. It separates prompt construction from LLM execution:

```kotlin
public class OptimizableNode<TInput, TOutput>(
    name: String,
    public val inputField: String?,          // key in Example.data for this node's input (null if not mapping to Examples)
    public val outputField: String?,         // key in Example.data for this node's output
    public val instruction: String,          // base instruction; overridable via OptimizationConfig
    public val promptFn: OptimizablePromptFn<TInput, TOutput>,  // builds Prompt from (instruction, demos, input)
    internal val executePrompt: suspend AIAgentGraphContextBase.(Prompt) -> TOutput,  // LLM call + response parsing
    public val description: String? = null,  // for MIPRO program description
    public val demonstrations: List<Demonstration<TInput, TOutput>> = emptyList(),  // default few-shot demos
    inputType: KType,
    outputType: KType,
) : AIAgentNode<TInput, TOutput>(name, inputType, outputType, execute = { input ->
    val config = coroutineContext[OptimizationConfig]
    val effectiveInstruction = config?.getInstruction(name) ?: instruction
    val effectiveDemos = config?.getTypedDemonstrations<TInput, TOutput>(name) ?: demonstrations
    val builtPrompt = promptFn(effectiveInstruction, effectiveDemos, input)
    executePrompt(builtPrompt)
})
```

Key points:
- `promptFn` is a **pure function** `(instruction, demos, input) -> Prompt`. It does not call the LLM.
- `executePrompt` handles the LLM call. For `String` output: `promptExecutor.execute()`. For typed output: `promptExecutor.executeStructured()`.
- The `execute` lambda (inherited from `AIAgentNode`) is derived automatically — users don't write it.
- `inputField`/`outputField` are nullable. When null, the node can't be linked to `Example` data (e.g., when demonstrations are provided directly at construction time).

### OptimizablePromptFn

```kotlin
public typealias OptimizablePromptFn<TInput, TOutput> = (
    instruction: String,
    demos: List<Demonstration<TInput, TOutput>>,
    input: TInput,
) -> Prompt
```

Two defaults are provided:

- **`defaultStringPromptFn`** — for `String -> String` nodes. Passes raw text as system/user/assistant messages.
- **`defaultPromptFn(inputSerializer, outputSerializer)`** — for generic typed nodes. JSON-encodes demonstration inputs/outputs and the node input.

### DSL

Nodes are created via the `optimizableNode()` DSL function inside a strategy builder:

```kotlin
val myStrategy = strategy("classifier") {
    // String -> String (simplest case)
    val classify by optimizableNode(
        instruction = "Classify the sentiment of the text.",
        inputField = "text",
        outputField = "sentiment",
    )

    // With pre-existing demonstrations
    val summarize by optimizableNode(
        instruction = "Summarize the article.",
        demonstrations = listOf(
            Demonstration("Long article...", "Brief summary."),
        ),
    )

    // Generic typed output (uses JSON prompt + structured output by default)
    val extract by optimizableNode<String, PersonInfo>(
        instruction = "Extract person information.",
        inputField = "text",
        outputField = "person",
    )

    edge(nodeStart forwardTo classify)
    edge(classify forwardTo summarize)
    edge(summarize forwardTo extract)
    edge(extract forwardTo nodeFinish)
}
```

Both `inputField` and `outputField` default to `null`. They are only needed when the node participates in `Example`-based optimization (e.g., `LabeledFewShot`).

There are two overloads:
- `optimizableNode(instruction, ...)` — `String -> String`, uses `defaultStringPromptFn` and plain text LLM execution.
- `optimizableNode<TInput, TOutput>(instruction, ...)` — generic types (annotated `@JvmName("optimizableNodeTyped")`), uses `defaultPromptFn` with JSON serialization and `executeStructured<TOutput>()`.

### Optimization Config via Coroutine Context

```kotlin
class OptimizationConfig(
    val instructions: Map<String, String> = emptyMap(),   // nodeName -> instruction override
    val demonstrations: Map<String, List<Demonstration<*, *>>> = emptyMap(),  // nodeName -> demos override
) : CoroutineContext.Element

// Parallel evaluation of different configs
val results = configs.map { config ->
    async {
        withContext(config) {
            agent.run(input)
        }
    }
}.awaitAll()
```

`OptimizableNode`'s execute lambda checks the coroutine context first, then falls back to node defaults. This happens automatically — users don't write context-reading code.

### Trace Collection Feature

Captures node inputs/outputs during execution for bootstrapping:

```kotlin
val agent = AIAgent(strategy = myStrategy) {
    collectTraces {
        collectOnlyOptimizable = true   // only OptimizableNode instances (default)
        maxTracesPerNode = 100
    }
}

agent.run(input)

val traces = agent.feature(TraceCollectionFeature)?.collectedTraces
val nodeTraces = traces?.getTracesForNode("classify")
```

The feature checks `node is OptimizableNode<*, *>` to determine which nodes to trace.

### Strategy Optimizer Interface

```kotlin
public interface StrategyOptimizer {
    suspend fun <TInput, TOutput> optimize(
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        trainset: Dataset,
        valset: Dataset? = null,
        metric: Metric<TOutput>,
    ): OptimizationResult
}
```

Returns `OptimizationResult(config, score, iterations, metadata)`. The `config` can be used directly via coroutine context, or eventually baked into node defaults.

### Example and Metric Types

```kotlin
// Training data — values can be any type matching node I/O
data class Example(
    val data: Map<String, Any>,
    val labelKey: String? = null,
)

// Typed metric — T matches the strategy's output type
typealias Metric<T> = (expected: T, actual: T) -> Double
```

### Strategy Utilities

`StrategyUtils.kt` provides:
- `findAllNodes()` — all `AIAgentNode` instances in a strategy
- `findOptimizableModules()` — all `OptimizableNode` instances
- `getOptimizableNodeNames()` — names of optimizable nodes
- `extractOptimizationConfig()` — captures current instruction values
- `validateOptimizationConfig(config)` — checks config matches strategy
- `describeForOptimization()` — text description for MIPRO instruction proposal

### Context Helpers

`OptimizationContextHelpers.kt` provides utilities for custom node lambdas that need to read optimization config:
- `getNodeInstruction(nodeName, default)` — instruction from context or default
- `getNodeDemonstrations<TInput, TOutput>(nodeName, default)` — demos from context or default
- `hasOptimizationConfig()` / `getOptimizationConfig()` — check/get config

These are **not** used by `OptimizableNode` itself (it accesses `OptimizationConfig` directly in its execute lambda), but are available for custom scenarios.

## MIPRO v2 Algorithm (Reference)

Three-step optimization pipeline:

### Step 1: Bootstrap Few-Shot Examples
- Generate N diverse demo candidate sets per optimizable node
- Strategies: zero-shot, labeled-only, bootstrapped (with/without metric threshold)
- Result: `Map<nodeName, List<List<Demonstration>>>`

### Step 2: Propose Instruction Candidates
- Use LLM meta-prompting to generate instruction variants
- Context: dataset summary, program description, demo examples, random tips
- Result: `Map<nodeName, List<String>>`

### Step 3: Configuration Search
- Search space: (instruction, demo_set) per node
- Currently: random search (Bayesian/TPE is future work)
- Evaluate candidates on validation set
- Return best configuration

## BootstrapFewShot Algorithm (Detailed)

BootstrapFewShot generates demonstrations by running a "teacher" agent on training data and keeping traces from successful executions. It is Step 1 of the MIPRO v2 pipeline (above), and also a standalone optimizer.

### Algorithm Flow

1. **Teacher pre-optimization**: If `maxLabeledDemos > 0` and the teacher isn't already optimized, run `LabeledFewShot(k=maxLabeledDemos)` on the teacher first. This gives the teacher a baseline of good demonstrations.

2. **Bootstrap loop**: For each training example (until `maxBootstrappedDemos` traces collected):
   - **Filter teacher demos**: Remove any teacher demonstrations that would leak the current example's ground truth (prevent data leakage).
   - **Run teacher**: Execute the teacher agent on the example, collecting per-node traces via `TraceCollectionFeature`.
   - **Evaluate**: Run `metric(expected, actual)` against `metricThreshold`. If no metric is provided, all completions are accepted.
   - **On success**: Store per-node traces. Move to next example.
   - **On metric failure**: Retry up to `maxRounds` times. If all rounds fail, add example to fallback pool.
   - **On exception**: Increment error counter. If `maxErrors` exceeded, stop bootstrapping entirely.

3. **Train student**: For each optimizable node:
   - Take up to `maxBootstrappedDemos` bootstrapped traces (prioritized).
   - Fill remaining slots with labeled examples from the fallback pool (examples that failed to bootstrap), up to `maxLabeledDemos` total.
   - Combine into demonstrations for the node.

### Trace Selection

When a single example produces **multiple traces** for one node (e.g., from multiple rounds or retry paths):
- 50% chance: sample uniformly from the first N-1 traces (exploration)
- 50% chance: take the last trace (exploitation — most recent/refined)
- Deterministic per-trace via seeded random.

### Outcome Types

Each bootstrap attempt produces one of:
- **Success**: Metric passed → per-node traces stored. `Map<nodeName, Trace>`
- **MetricNotPassed**: Expected failure → retry next round, benign.
- **ExceptionRaised**: Runtime error → increments error counter, treated as hard failure.

### Key Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `maxBootstrappedDemos` | 4 | Max bootstrapped traces to collect per node |
| `maxLabeledDemos` | 16 | Max total demo slots (bootstrapped fill first, labeled fill remainder) |
| `maxRounds` | 1 | Retry attempts per example |
| `maxErrors` | null | Cap on exception count before stopping (null = unlimited) |
| `metric` | null | Evaluation function `(expected, actual) -> Double` |
| `metricThreshold` | 1.0 | Required metric score for a trace to be considered successful |

### Reference Implementation Files

- Algorithm: `koog-auto-agent-optimization/src/main/kotlin/promptOptimization/boostrap/BootstrapFewShot.kt`
- Tests: `koog-auto-agent-optimization/src/test/kotlin/promptOptimization/BootstrapFewShotTest.kt`
- Koog-specific variant: `koog-auto-agent-optimization/src/main/kotlin/agentOptimization/optimizationFramework/optimizerImplementations/BootstrapFewShotOptimizer.kt`

## File Structure

```
agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/
└── optimization/                            # Optimization package (all new files)
    ├── OptimizableNode.kt                   # DONE: OptimizableNode, OptimizableNodeDelegate, DSL, prompt fns
    │
    ├── core/
    │   ├── Demonstration.kt                 # DONE: Typed input-output pair
    │   ├── OptimizationConfig.kt            # DONE: Coroutine context element for trial configs
    │   ├── StrategyOptimizer.kt             # DONE: Optimizer interface + OptimizationResult + StrategyOptimizerConfig
    │   └── Example.kt                       # DONE: Training data type (Map<String, Any>) + Metric<T>
    │
    ├── features/
    │   └── TraceCollectionFeature.kt        # DONE: Pipeline feature for capturing node I/O
    │
    ├── dsl/
    │   └── OptimizationContextHelpers.kt    # DONE: getNodeInstruction(), getNodeDemonstrations(), etc.
    │
    ├── optimizers/
    │   ├── LabeledFewShot.kt                # DONE: Samples labeled examples as demonstrations
    │   ├── BootstrapFewShot.kt              # TODO
    │   └── mipro/
    │       ├── MIPROv2Optimizer.kt           # TODO
    │       ├── DemoSetGenerator.kt           # TODO
    │       ├── InstructionProposer.kt        # TODO
    │       └── ConfigurationSearch.kt        # TODO
    │
    └── util/
        ├── StrategyUtils.kt                 # DONE: findOptimizableModules(), validateOptimizationConfig(), etc.
        └── Evaluation.kt                    # TODO: Metric evaluation helpers

agents/agents-core/src/jvmTest/kotlin/ai/koog/agents/core/optimization/
└── OptimizationInfrastructureTest.kt        # DONE: Tests for infrastructure + OptimizableNode DSL
```

No Koog core files are modified — `AIAgentNode`, `AIAgentNodeDelegate`, and `AIAgentSubgraphBuilder` are untouched.

## Implementation Status

### Done
- [x] `Demonstration<TInput, TOutput>` data class
- [x] `OptimizableNode<TInput, TOutput>` subclass with prompt construction + LLM execution
- [x] `optimizableNode()` DSL (String and generic overloads)
- [x] Default prompt functions (`defaultStringPromptFn`, `defaultPromptFn` with JSON serialization)
- [x] Structured output via `executeStructured<T>()` for typed nodes
- [x] `OptimizationConfig` coroutine context element with typed accessors
- [x] `TraceCollectionFeature` with `collectTraces {}` DSL
- [x] Context helpers (`getNodeInstruction`, `getNodeDemonstrations`, etc.)
- [x] `StrategyOptimizer` interface + `OptimizationResult` + `StrategyOptimizerConfig`
- [x] `Example` (with `Map<String, Any>`) and `Metric<T>` types
- [x] Strategy utilities (`findOptimizableModules`, `validateOptimizationConfig`, `describeForOptimization`)
- [x] `LabeledFewShot` optimizer
- [x] Infrastructure tests (`OptimizationInfrastructureTest`)

### TODO
- [ ] Port `BootstrapFewShot` optimizer
- [ ] Port MIPRO v2 (`DemoSetGenerator`, `InstructionProposer`, `ConfigurationSearch`, `MIPROv2Optimizer`)
- [ ] Bake-in mechanism: apply `OptimizationConfig` as node defaults for deployment without coroutine context
- [ ] Metric evaluation helpers
- [ ] End-to-end integration tests (with mocked LLM)
- [ ] Port heart disease example

## Bake-In Mechanism (Design Sketch)

After optimization, users have two ways to use the result:

1. **Coroutine context (current):** `withContext(result.config) { agent.run(input) }` — works today.
2. **Bake-in (future):** Create new `OptimizableNode` instances with instruction/demonstrations set as defaults, rebuilding the strategy. This eliminates the need for coroutine context wrappers in production.

The bake-in approach requires either:
- A `copy()`-like mechanism on `OptimizableNode` to produce a new node with updated defaults, or
- A strategy-level rebuild that replaces nodes

Implementation is deferred until the optimizer pipeline is more complete.

## Key Differences from Reference Implementation

| Aspect | Reference (`promptOptimization/`) | Koog Integration |
|--------|-----------------------------------|------------------|
| Agent container | Custom `Agent` class | Native `AIAgentGraphStrategy` (unchanged) |
| Module/predictor | Custom `AgentModule` | `OptimizableNode` subclass of `AIAgentNode` |
| Prompt construction | `PromptFn` closure calling LLM | `OptimizablePromptFn` returning `Prompt` (pure) |
| LLM execution | Inside `AgentModule.forward()` | `executePrompt` internal to `OptimizableNode` |
| Structured output | Custom schema injection | Koog's `executeStructured<T>()` |
| Type handling | `Signature.inputType/outputType` | Node's existing `KType` generics |
| Execution + tracing | Custom `forwardWithTrace()` | Koog strategy execution + `TraceCollectionFeature` |
| Parallel evaluation | Creates copies of Agent | Coroutine context with `OptimizationConfig` |
| Immutability | Copy Agent/AgentModule instances | Shared strategy, config via coroutine context |

## Open Questions

1. **Subgraph optimization:** Should entire subgraphs be optimizable units, or just individual LLM-calling nodes within them? Current design targets nodes.

2. **Serialization:** How to save/load optimized configurations? Consider kotlinx.serialization for `OptimizationConfig` and `Demonstration`.

3. **Multi-model support:** Should different nodes support different LLM models during optimization? Current design uses the model from the agent's LLM context.

4. **Demo type erasure:** `OptimizationConfig` stores `List<Demonstration<*, *>>`. Typed access via `getTypedDemonstrations<TInput, TOutput>()` performs an unchecked cast. This works but isn't fully type-safe at the config level.

5. **Agent execution for BootstrapFewShot:** `StrategyOptimizer.optimize()` takes a `strategy`, but `BootstrapFewShot` needs to actually execute an agent (with tools, features, LLM executor) to collect traces. Options:
   - (a) Accept an agent runner/factory in the `BootstrapFewShot` constructor, still implement `StrategyOptimizer`
   - (b) Widen the `StrategyOptimizer` interface to optionally accept an agent or agent factory
   - (c) Use a separate interface (e.g., `AgentOptimizer`) that takes an agent directly

   The reference implementation takes the full agent. Option (a) keeps the interface stable while giving BootstrapFewShot what it needs.

## Design Principles

1. **Separate type, clean base:** `OptimizableNode` subclass keeps optimization concerns out of `AIAgentNode`. Regular nodes are completely unaffected.
2. **Node owns its pieces:** Instruction, demonstrations, prompt function, and execution are all properties of the node, not hidden in closures.
3. **Prompt construction is pure:** `promptFn` returns a `Prompt` object. LLM call and response parsing are separate.
4. **Immutable evaluation:** Config passed via coroutine context; strategy/nodes never mutated during optimization.
5. **Type safety:** Leverage Kotlin generics, Koog's `KType`, and `executeStructured<T>()`.
6. **Gradual adoption:** Only `OptimizableNode`s participate in optimization. Regular `node()` is unchanged.
7. **Parallel-safe:** Each evaluation coroutine isolated via coroutine context.
