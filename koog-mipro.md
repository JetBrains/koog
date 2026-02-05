# Koog MIPRO Integration - Design Document

This document describes the integration of MIPRO v2 prompt optimization into Koog's native abstractions.

## Goal

Extend Koog's existing `AIAgentNode` and `AIAgentGraphStrategy` types to support automatic prompt optimization via MIPRO v2. Users should be able to add optimization metadata (instruction, demonstrations) to nodes and run an optimizer that finds the best configuration.

## Reference Materials

- **MIPRO paper:** `koog-auto-agent-optimization/MIPRO.pdf`
- **Current MIPRO implementation:** `koog-auto-agent-optimization/src/main/kotlin/promptOptimization/`
- **Koog abstractions summary:** `koog-auto-agent-optimization/koog-abstractions.md`
- **Koog source:** `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/`

## Understanding the Current State

### MIPRO Implementation Abstractions

The current implementation in `koog-auto-agent-optimization/` uses these core types:

| Type | Purpose |
|------|---------|
| `Agent` | Container for multiple `AgentModule`s, represents the full program |
| `AgentModule` | Single LLM call unit with instruction, traces, and prompt construction |
| `Signature` | Task spec: instruction string + input/output `KType` |
| `Trace` | A Koog `Prompt` representing a few-shot demonstration |
| `Example` | Training data with input map + optional label |
| `Optimizer` | Interface: `optimize(agent, trainset, valset) -> Agent` |

Key methods:
- `AgentModule.withConfiguration(instruction, traces)` - immutable update
- `Agent.withModules(newModules)` - immutable update
- `Agent.forwardWithTraces(batch)` - execute and collect traces for bootstrapping

### Koog Native Abstractions

Koog's strategy/node system:

| Type | Purpose |
|------|---------|
| `AIAgentGraphStrategy<TInput, TOutput>` | Graph of nodes with start/finish, defines workflow |
| `AIAgentNode<TInput, TOutput>` | Single processing step with `execute` lambda |
| `AIAgentSubgraph<TInput, TOutput>` | Encapsulated sub-workflow, is itself a node |
| `AIAgentEdge<In, Out>` | Conditional connection between nodes |

Key properties of nodes:
- `name: String` - identifier
- `inputType: KType`, `outputType: KType` - type information
- `execute: suspend AIAgentGraphContextBase.(TInput) -> TOutput` - the logic

Nodes access LLMs via context:
```kotlin
val myNode by node<String, String> { input ->
    llm.writeSession {
        appendPrompt {
            system("You are a helpful assistant.")
            user(input)
        }
        requestLLM().content
    }
}
```

### Gap Analysis

| What MIPRO Needs | Koog Has | Gap |
|------------------|----------|-----|
| Instruction string to optimize | Prompts built inline in node lambdas | Need to externalize/parameterize instruction |
| Few-shot demonstrations | No native concept | Need to add demonstration storage |
| Module-level tracing | Pipeline events exist | Need trace collection feature |
| Parallel evaluation with different configs | Single execution path | Need config-via-context mechanism |

## Architecture Design

### Key Design Decisions

1. **Extend existing types:** Add optional optimization fields directly to `AIAgentNode` rather than creating wrapper types. This keeps a single type hierarchy.

2. **Configuration via coroutine context:** During optimization, pass candidate configurations through Kotlin's coroutine context. This enables parallel evaluation without mutating nodes or copying strategy graphs.

3. **Pipeline feature for trace collection:** Use Koog's existing pipeline mechanism to capture node inputs/outputs during bootstrap execution.

4. **Immutable nodes, shared strategy:** Nodes have `copy()` for creating variants. The strategy graph structure is never copied during optimization - only the config changes per evaluation.

5. **Leverage Koog's type system:** Nodes are already generic with `KType`. No need to duplicate in signatures.

### Concept Mapping

| MIPRO Concept | Koog Integration |
|---------------|------------------|
| `Agent` | `AIAgentGraphStrategy` (unchanged structure) |
| `AgentModule` | `AIAgentNode` (extended with optimization fields) |
| `Signature.instruction` | `AIAgentNode.instruction: String?` |
| `Trace` | `Demonstration<TInput, TOutput>` |
| `Optimizer` | `StrategyOptimizer` interface |

### Extended AIAgentNode (IMPLEMENTED)

Added optional optimization fields to the existing `AIAgentNode`:

```kotlin
public open class AIAgentNode<TInput, TOutput> internal constructor(
    override val name: String,
    override val inputType: KType,
    override val outputType: KType,
    public val execute: suspend AIAgentGraphContextBase.(input: TInput) -> TOutput,
    // Optimization fields
    public val instruction: String? = null,
    public val demonstrations: List<Demonstration<TInput, TOutput>> = emptyList(),
    public val description: String? = null,
) : AIAgentNodeBase<TInput, TOutput>() {

    /** Create a copy with updated optimization fields */
    public fun copy(
        instruction: String? = this.instruction,
        demonstrations: List<Demonstration<TInput, TOutput>> = this.demonstrations,
        description: String? = this.description,
    ): AIAgentNode<TInput, TOutput> = AIAgentNode(
        name = name,
        inputType = inputType,
        outputType = outputType,
        execute = execute,
        instruction = instruction,
        demonstrations = demonstrations,
        description = description,
    )
    // ... existing execute logic unchanged
}
```

### Demonstration Type (IMPLEMENTED)

Located at `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/optimization/core/Demonstration.kt`:

```kotlin
/**
 * A typed input-output pair for few-shot learning.
 *
 * Demonstrations are used to provide examples to LLM nodes during prompt optimization.
 * They can be manually created from labeled data or automatically generated via bootstrapping.
 */
public data class Demonstration<TInput, TOutput>(
    val input: TInput,
    val output: TOutput,
    val isBootstrapped: Boolean = false,  // true if generated via bootstrap
)
```

### Optimization Config via Coroutine Context

During optimization, pass candidate configurations through coroutine context to enable parallel evaluation:

```kotlin
/**
 * Immutable configuration for a single optimization trial.
 * Passed via coroutine context, not by mutating nodes.
 */
class OptimizationConfig(
    val instructions: Map<String, String>,  // nodeName -> instruction
    val demonstrations: Map<String, List<Demonstration<*, *>>>,  // nodeName -> demos
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<OptimizationConfig>
}

// Parallel evaluation of different configs
val results = configs.map { config ->
    async {
        withContext(OptimizationConfig(config.instructions, config.demonstrations)) {
            agent.run(input)
        }
    }
}.awaitAll()
```

### Accessing Config in Node Execute Lambda

Nodes read from coroutine context, falling back to node's default fields:

```kotlin
val classifyNode by node<String, Classification>(
    instruction = "Classify the input text",  // default instruction
) { input ->
    // Check coroutine context for optimization override
    val instruction = coroutineContext[OptimizationConfig]?.instructions?.get(name)
        ?: this@node.instruction
        ?: error("No instruction for node $name")

    val demos = coroutineContext[OptimizationConfig]?.demonstrations?.get(name)
        ?: this@node.demonstrations

    llm.writeSession {
        appendPrompt {
            system(instruction)
            // Format demonstrations as few-shot examples
            for (demo in demos) {
                user(formatInput(demo.input))
                assistant(formatOutput(demo.output))
            }
            user(formatInput(input))
        }
        requestLLMStructured<Classification>()
    }
}
```

### Trace Collection Feature

Use Koog's pipeline mechanism to capture node I/O during bootstrap:

```kotlin
/**
 * Pipeline feature that captures inputs/outputs of optimizable nodes.
 */
class TraceCollectionFeature : AIAgentFeature<TraceCollectionFeature.Config, TraceCollectionFeature.State> {

    class Config
    class State {
        val traces = mutableMapOf<String, MutableList<Demonstration<Any?, Any?>>>()
    }

    override fun onNodeExecutionCompleted(
        eventId: String,
        info: ExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        output: Any?,
        outputType: KType
    ) {
        // Only capture for nodes with optimization metadata
        if (node is AIAgentNode<*, *> && node.instruction != null) {
            val state = context.getFeatureState(this)
            state.traces.getOrPut(node.name) { mutableListOf() }
                .add(Demonstration(input, output, isBootstrapped = true))
        }
    }
}

// Usage
val traceFeature = TraceCollectionFeature()
val agent = AIAgent(strategy = myStrategy) { install(traceFeature) }
agent.run(input)
val collectedTraces = traceFeature.state.traces  // Map<nodeName, List<Demonstration>>
```

### Strategy Optimizer Interface

```kotlin
/**
 * Optimizer that finds best instruction/demonstration configuration for a strategy.
 */
interface StrategyOptimizer {
    /**
     * Optimize the strategy's nodes and return a new strategy with best config baked in.
     *
     * @param strategy The strategy to optimize (nodes must have instruction set)
     * @param trainset Training examples
     * @param valset Validation examples (optional, will split trainset if null)
     * @return New strategy with optimized instruction/demonstrations in nodes
     */
    suspend fun <TInput, TOutput> optimize(
        strategy: AIAgentGraphStrategy<TInput, TOutput>,
        trainset: List<Example>,
        valset: List<Example>? = null,
    ): AIAgentGraphStrategy<TInput, TOutput>
}
```

### Baking Optimized Config into Strategy

After optimization finds the best config, create new node instances with the winning values:

```kotlin
fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.withOptimizedConfig(
    config: OptimizationConfig
): AIAgentGraphStrategy<TInput, TOutput> {
    // For each optimizable node, create a copy with the optimized instruction/demos
    // This requires rebuilding the strategy with the new nodes
    // Implementation details TBD based on Koog's strategy builder internals
}
```

### DSL Extensions (IMPLEMENTED)

Extended Koog's existing `node` DSL to accept optimization parameters:

```kotlin
// Extended node builder with optimization fields (in AIAgentSubgraphBuilderBase)
public inline fun <reified Input, reified Output> node(
    name: String? = null,
    instruction: String? = null,
    demonstrations: List<Demonstration<Input, Output>> = emptyList(),
    description: String? = null,
    noinline execute: suspend AIAgentGraphContextBase.(input: Input) -> Output
): AIAgentNodeDelegate<Input, Output>

// Usage - looks like normal Koog, just with extra parameters
val myStrategy = strategy<String, Classification>("classifier") {
    val classify by node<String, Classification>(
        instruction = "Classify the input text into categories",
        description = "Main classification step",
    ) { input ->
        // Read instruction from context (optimization) or node default
        val instruction = coroutineContext[OptimizationConfig]?.instructions?.get(name)
            ?: this@node.instruction!!

        val demos = coroutineContext[OptimizationConfig]?.demonstrations?.get(name)
            ?: this@node.demonstrations

        llm.writeSession {
            appendPrompt {
                system(instruction)
                for (demo in demos) {
                    user(formatInput(demo.input))
                    assistant(formatOutput(demo.output))
                }
                user(formatInput(input))
            }
            requestLLMStructured<Classification>().getOrThrow()
        }
    }

    nodeStart then classify then nodeFinish
}

// Optimization
val optimizer = MIPROv2Optimizer(config)
val optimizedStrategy = optimizer.optimize(myStrategy, trainset, valset)

// Execution with optimized strategy
val agent = AIAgent(strategy = optimizedStrategy, ...)
val result = agent.run(input)
```

### Helper for Instruction/Demo Access

To reduce boilerplate in node lambdas:

```kotlin
// Extension on AIAgentGraphContextBase
suspend fun AIAgentGraphContextBase.getNodeInstruction(nodeName: String, default: String?): String {
    return coroutineContext[OptimizationConfig]?.instructions?.get(nodeName)
        ?: default
        ?: error("No instruction for node $nodeName")
}

suspend inline fun <reified TInput, reified TOutput> AIAgentGraphContextBase.getNodeDemonstrations(
    nodeName: String,
    default: List<Demonstration<TInput, TOutput>>
): List<Demonstration<TInput, TOutput>> {
    @Suppress("UNCHECKED_CAST")
    return coroutineContext[OptimizationConfig]?.demonstrations?.get(nodeName) as? List<Demonstration<TInput, TOutput>>
        ?: default
}

// Cleaner node lambda
val classify by node<String, Classification>(
    instruction = "Classify the input text",
) { input ->
    val instruction = getNodeInstruction(name, this@node.instruction)
    val demos = getNodeDemonstrations<String, Classification>(name, this@node.demonstrations)
    // ...
}
```

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

## File Structure

```
agents/agents-core/src/commonMain/kotlin/ai/koog/agents/
├── core/agent/entity/
│   └── AIAgentNode.kt                  # DONE: Added instruction, demonstrations, description fields + copy()
│
├── core/dsl/builder/
│   ├── AIAgentNodeDelegate.kt          # DONE: Added optimization fields, passes through to AIAgentNode
│   └── AIAgentSubgraphBuilder.kt       # DONE: Extended node() builder with optimization params
│
└── optimization/                        # NEW PACKAGE
    ├── core/
    │   ├── Demonstration.kt            # DONE: Typed input-output pair
    │   ├── OptimizationConfig.kt       # DONE: Coroutine context element for trial configs
    │   ├── StrategyOptimizer.kt        # DONE: Optimizer interface + OptimizationResult
    │   └── Example.kt                  # DONE: Training data type + Metric typealias + Metrics object
    │
    ├── features/
    │   └── TraceCollectionFeature.kt   # DONE: Pipeline feature for capturing node I/O
    │
    ├── dsl/
    │   ├── OptimizationContextHelpers.kt  # DONE: getNodeInstruction(), getNodeDemonstrations()
    │   └── DemonstrationFormatting.kt     # TODO: Helpers for demo -> prompt messages
    │
    ├── optimizers/                        # TODO: Phase 3
    │   ├── LabeledFewShot.kt
    │   ├── BootstrapFewShot.kt
    │   └── mipro/
    │       ├── MIPROv2Optimizer.kt
    │       ├── DemoSetGenerator.kt
    │       ├── InstructionProposer.kt
    │       └── ConfigurationSearch.kt
    │
    └── util/
        ├── StrategyUtils.kt            # DONE: findOptimizableNodes(), validateOptimizationConfig(), etc.
        ├── SchemaGeneration.kt         # TODO: Port from current impl if needed
        └── Evaluation.kt               # TODO: Metric evaluation helpers

examples/
└── optimization/
    └── heartDisease/
        ├── HeartDiseaseStrategy.kt     # Strategy with optimizable nodes
        └── RunOptimization.kt          # Main optimization runner
```

## Implementation Phases

### Phase 1: Extend Koog Core Types (COMPLETED)
- [x] Modify `AIAgentNode` to add optional `instruction`, `demonstrations`, `description` fields
- [x] Add `copy()` method to `AIAgentNode` for creating variants
- [x] Extend `node()` DSL builder to accept optimization parameters
- [x] Extend `AIAgentNodeDelegate` to pass through optimization fields
- [x] Create `Demonstration<TInput, TOutput>` data class

**Files changed:**
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/entity/AIAgentNode.kt`
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/builder/AIAgentNodeDelegate.kt`
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/builder/AIAgentSubgraphBuilder.kt`
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/optimization/core/Demonstration.kt` (new)

**Note:** The `AIAgentNodeDelegate.transform()` method throws `NotImplementedError` if the node has demonstrations, because the transformation is a suspend function that cannot be applied to demonstrations at delegate construction time.

### Phase 2: Optimization Infrastructure (COMPLETED)
- [x] Create `OptimizationConfig` coroutine context element
- [x] Implement `TraceCollectionFeature` for capturing node I/O
- [x] Create context helper functions (`getNodeInstruction()`, `getNodeDemonstrations()`)
- [x] Create `StrategyOptimizer` interface
- [x] Create strategy utility functions (`findOptimizableNodes()`, `validateOptimizationConfig()`, etc.)
- [x] Create `Example` and `Metric` types

**Files added:**
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/optimization/core/OptimizationConfig.kt`
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/optimization/core/Example.kt`
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/optimization/core/StrategyOptimizer.kt`
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/optimization/dsl/OptimizationContextHelpers.kt`
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/optimization/features/TraceCollectionFeature.kt`
- `agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/optimization/util/StrategyUtils.kt`

**Note:** The `withOptimizedConfig()` full implementation is deferred. The primary mechanism for optimization is passing configs via coroutine context. Strategy utility functions provide the ability to inspect, validate, and extract configs from strategies.

### Phase 3: Port Optimizers
- [ ] Port `LabeledFewShot` optimizer
- [ ] Port `BootstrapFewShot` optimizer
- [ ] Port MIPRO v2 Step 1: `DemoSetGenerator`
- [ ] Port MIPRO v2 Step 2: `InstructionProposer`
- [ ] Port MIPRO v2 Step 3: `ConfigurationSearch`
- [ ] Port `MIPROv2Optimizer` main class

### Phase 4: Integration & Testing
- [ ] Port heart disease example to new abstractions
- [ ] Unit tests for extended node types
- [ ] Unit tests for TraceCollectionFeature
- [ ] Integration tests for optimizers
- [ ] Verify parallel evaluation works correctly
- [ ] Benchmark against current implementation

## Key Differences from Current Implementation

| Aspect | Current (`promptOptimization/`) | New Design |
|--------|--------------------------------|------------|
| Agent container | Custom `Agent` class | Native `AIAgentGraphStrategy` (unchanged) |
| Module/predictor | Custom `AgentModule` | Extended `AIAgentNode` with optional fields |
| Prompt construction | `PromptFn` type alias | Koog DSL + context helpers |
| Type handling | `Signature.inputType/outputType` | Node's existing `KType` generics |
| Execution | Custom `forwardWithTrace()` | Koog's strategy execution + TraceCollectionFeature |
| Structured output | Custom schema injection | Koog's `requestLLMStructured<T>()` |
| Parallel evaluation | Creates copies of Agent | Coroutine context with OptimizationConfig |
| Immutability | Copy Agent/AgentModule instances | Shared strategy, config via coroutine context |

## Open Questions

1. **Subgraph optimization:** Should entire subgraphs be optimizable units, or just individual LLM-calling nodes within them? Current design targets nodes.

2. **Serialization:** How to save/load optimized configurations? Consider kotlinx.serialization for `OptimizationConfig` and `Demonstration`.

3. **Multi-model support:** Should different nodes support different LLM models during optimization? (Current design assumes uniform model)

4. **Strategy rebuilding:** The `withOptimizedConfig()` function needs to create new node instances and rebuild the strategy. Need to understand Koog's strategy builder internals to implement this correctly.

5. **Demo type erasure:** `Demonstration<TInput, TOutput>` in `OptimizationConfig` uses `List<Demonstration<*, *>>` which loses type info. May need runtime casting or separate type-safe accessors.

## Design Principles

1. **Extend, don't wrap:** Add to existing Koog types rather than creating parallel hierarchies
2. **Immutable evaluation:** Config passed via coroutine context, strategy/nodes never mutated during optimization
3. **Type safety:** Leverage Kotlin generics and Koog's existing type system
4. **DSL consistency:** Extended `node()` builder feels like natural Koog
5. **Gradual adoption:** Nodes without instruction/demos work exactly as before
6. **Parallel-safe:** Each evaluation coroutine isolated via coroutine context
