# Koog MIPRO Integration - Design Document

This document describes the integration of MIPRO v2 prompt optimization into Koog's native abstractions.

## Goal

Extend Koog's DSL primitives (`strategy`, `node`, `subgraph`) to support automatic prompt optimization via MIPRO v2. Users should be able to mark nodes as "optimizable" and run an optimizer that returns an improved strategy with tuned instructions and few-shot demonstrations.

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
| Module-level tracing | Pipeline events exist | Need trace collection mechanism |
| Immutable strategy copies | Strategies are mutable (edges list) | Need wrapper or copy mechanism |

## Architecture Design

### Concept Mapping

| MIPRO Concept | New Koog Integration |
|---------------|---------------------|
| `Agent` | `OptimizableStrategy<TInput, TOutput>` |
| `AgentModule` | `OptimizableNode<TInput, TOutput>` |
| `Signature` | `NodeSignature` (instruction + descriptions only) |
| `Trace` | `Demonstration<TInput, TOutput>` |
| `Optimizer` | `StrategyOptimizer` |

### Core Design Decisions

1. **Wrapper pattern:** Create wrapper types (`OptimizableNode`, `OptimizableStrategy`) that compose Koog types rather than modifying them. This maintains compatibility.

2. **Immutability:** All optimization operations return new instances:
   - `OptimizableNode.withInstruction()` → new node
   - `OptimizableStrategy.withNodes()` → new strategy

3. **Leverage Koog's type system:** Nodes are already generic with `KType`. No need to duplicate in signatures.

4. **Koog's prompt construction:** Use Koog's prompt DSL with parameterized templates, not custom `PromptFn`.

5. **Explicit opt-in:** Only nodes marked as "optimizable" participate in optimization.

### New Types

```kotlin
/**
 * Optimizable aspects of an LLM-calling node.
 * Focuses on instruction (the optimization target) and optional field descriptions.
 * Input/output types come from the node's generics.
 */
data class NodeSignature(
    val instruction: String,
    val inputDescription: String? = null,
    val outputDescription: String? = null,
) {
    fun withInstruction(newInstruction: String) = copy(instruction = newInstruction)
}

/**
 * A demonstration is a typed input-output pair for few-shot learning.
 */
data class Demonstration<TInput, TOutput>(
    val input: TInput,
    val output: TOutput,
    val isBootstrapped: Boolean = false,
)

/**
 * Wraps node metadata for optimization. Does NOT wrap the node itself -
 * instead provides a factory to build nodes with current configuration.
 */
class OptimizableNode<TInput, TOutput>(
    val name: String,
    val signature: NodeSignature,
    val demonstrations: List<Demonstration<TInput, TOutput>>,
    val inputType: KType,
    val outputType: KType,
    val description: String? = null,
    private val nodeFactory: (NodeSignature, List<Demonstration<TInput, TOutput>>) -> AIAgentNode<TInput, TOutput>,
) {
    fun withInstruction(instruction: String): OptimizableNode<TInput, TOutput> =
        OptimizableNode(name, signature.withInstruction(instruction), demonstrations, inputType, outputType, description, nodeFactory)

    fun withDemonstrations(demos: List<Demonstration<TInput, TOutput>>): OptimizableNode<TInput, TOutput> =
        OptimizableNode(name, signature, demos, inputType, outputType, description, nodeFactory)

    fun withConfiguration(instruction: String, demos: List<Demonstration<TInput, TOutput>>): OptimizableNode<TInput, TOutput> =
        OptimizableNode(name, signature.withInstruction(instruction), demos, inputType, outputType, description, nodeFactory)

    fun toNode(): AIAgentNode<TInput, TOutput> = nodeFactory(signature, demonstrations)
}

/**
 * Strategy wrapper that tracks optimizable nodes and can rebuild the strategy.
 */
class OptimizableStrategy<TInput, TOutput>(
    val name: String,
    val optimizableNodes: Map<String, OptimizableNode<*, *>>,
    val description: String? = null,
    private val strategyFactory: (Map<String, OptimizableNode<*, *>>) -> AIAgentGraphStrategy<TInput, TOutput>,
) {
    fun withNodes(nodes: Map<String, OptimizableNode<*, *>>): OptimizableStrategy<TInput, TOutput> =
        OptimizableStrategy(name, nodes, description, strategyFactory)

    fun withNode(nodeName: String, node: OptimizableNode<*, *>): OptimizableStrategy<TInput, TOutput> =
        withNodes(optimizableNodes + (nodeName to node))

    fun toStrategy(): AIAgentGraphStrategy<TInput, TOutput> = strategyFactory(optimizableNodes)
}

/**
 * Optimizer interface.
 */
interface StrategyOptimizer {
    suspend fun <TInput, TOutput> optimize(
        strategy: OptimizableStrategy<TInput, TOutput>,
        trainset: List<Example>,
        valset: List<Example>? = null,
    ): OptimizableStrategy<TInput, TOutput>
}
```

### DSL Design

```kotlin
// Define an optimizable node
inline fun <reified TInput, reified TOutput> optimizableNode(
    name: String? = null,
    instruction: String,
    description: String? = null,
    noinline execute: suspend AIAgentGraphContextBase.(
        signature: NodeSignature,
        demonstrations: List<Demonstration<TInput, TOutput>>,
        input: TInput
    ) -> TOutput,
): OptimizableNodeDelegate<TInput, TOutput>

// Usage in strategy builder
val myStrategy = optimizableStrategy<String, String>("classifier") {
    val classify by optimizableNode<String, Classification>(
        instruction = "Classify the input text into categories",
        description = "Main classification step",
    ) { signature, demos, input ->
        llm.writeSession {
            appendPrompt {
                system(signature.instruction)
                // Format demonstrations as few-shot examples
                for (demo in demos) {
                    user(formatInput(demo.input))
                    assistant(formatOutput(demo.output))
                }
                user(formatInput(input))
            }
            parseOutput(requestLLMStructured<Classification>())
        }
    }

    nodeStart then classify then nodeFinish
}

// Optimization
val optimizer = MIPROv2Optimizer(config)
val optimized = optimizer.optimize(myStrategy, trainset, valset)

// Execution
val agent = AIAgent(strategy = optimized.toStrategy(), ...)
val result = agent.run(input)
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
src/main/kotlin/
├── koogOptimization/
│   ├── core/
│   │   ├── NodeSignature.kt
│   │   ├── Demonstration.kt
│   │   ├── OptimizableNode.kt
│   │   ├── OptimizableStrategy.kt
│   │   ├── StrategyOptimizer.kt
│   │   ├── Example.kt
│   │   └── Types.kt                    # Metric type alias
│   │
│   ├── dsl/
│   │   ├── OptimizableNodeDelegate.kt
│   │   ├── OptimizableStrategyBuilder.kt
│   │   └── DemonstrationFormatting.kt  # Helpers for demo -> prompt
│   │
│   ├── optimizers/
│   │   ├── LabeledFewShot.kt
│   │   ├── BootstrapFewShot.kt
│   │   └── mipro/
│   │       ├── MIPROv2Optimizer.kt
│   │       ├── DemoSetGenerator.kt
│   │       ├── InstructionProposer.kt
│   │       └── ConfigurationSearch.kt
│   │
│   └── util/
│       ├── SchemaGeneration.kt         # Port from current impl
│       └── Evaluation.kt
│
└── examples/
    └── heartDisease/
        └── HeartDiseaseOptimizable.kt
```

## Implementation Phases

### Phase 1: Core Abstractions
- [ ] `NodeSignature` data class
- [ ] `Demonstration<TInput, TOutput>` data class
- [ ] `OptimizableNode<TInput, TOutput>` with immutable updates
- [ ] `OptimizableStrategy<TInput, TOutput>` with node management
- [ ] `StrategyOptimizer` interface
- [ ] `Example` and `Metric` types

### Phase 2: DSL Builders
- [ ] `OptimizableNodeDelegate` for property delegation
- [ ] `optimizableNode { }` builder function
- [ ] `optimizableStrategy { }` builder function
- [ ] Demonstration formatting helpers

### Phase 3: Port Optimizers
- [ ] `LabeledFewShot` optimizer
- [ ] `BootstrapFewShot` optimizer
- [ ] MIPRO v2 Step 1: `DemoSetGenerator`
- [ ] MIPRO v2 Step 2: `InstructionProposer`
- [ ] MIPRO v2 Step 3: `ConfigurationSearch`
- [ ] `MIPROv2Optimizer` main class

### Phase 4: Integration & Testing
- [ ] Port heart disease example
- [ ] Unit tests for core abstractions
- [ ] Integration tests for optimizers
- [ ] Benchmark against current implementation

## Key Differences from Current Implementation

| Aspect | Current (`promptOptimization/`) | New Design |
|--------|--------------------------------|------------|
| Agent container | Custom `Agent` class | `OptimizableStrategy` wrapping Koog |
| Module/predictor | Custom `AgentModule` | `OptimizableNode` with factory |
| Prompt construction | `PromptFn` type alias | Koog DSL + parameterized template |
| Type handling | `Signature.inputType/outputType` | Node's `KType` generics |
| Execution | Custom `forwardWithTrace()` | Koog's strategy execution |
| Structured output | Custom schema injection | Koog's `requestLLMStructured<T>()` |

## Open Questions

1. **Trace collection during bootstrap:** How to intercept node execution to collect demonstrations? Options:
   - Custom node wrapper that logs inputs/outputs
   - Pipeline feature that captures node I/O
   - Explicit trace collection in the execute lambda

2. **Subgraph optimization:** Should entire subgraphs be optimizable units, or just individual LLM-calling nodes within them?

3. **Serialization:** How to save/load optimized configurations? Consider kotlinx.serialization for `NodeSignature` and `Demonstration`.

4. **Multi-model support:** Should different nodes support different LLM models during optimization?

## Design Principles

1. **Composition over modification:** Wrap Koog types, don't modify them
2. **Immutability:** All mutations return new instances
3. **Type safety:** Leverage Kotlin generics throughout
4. **DSL consistency:** Feel like natural Koog extension
5. **Gradual adoption:** Optimize individual nodes without restructuring
6. **Separation of concerns:** Optimization logic separate from execution
