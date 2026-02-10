# Outline
-Koog Graph Strategy ~ dspy program
- Koog GraphAIAgent node ~ dsyp predictor
(- subgraph ~ predictor ???)

We introduce a `optimizableNode` that is like a `node`, with two additional fields that get optimized:
- instruction: 
- demonstrations:
We specifiy a default `execute` lambda for an optimizableNode, that corresponds to the following prompt
```
system(instruction)
user(few shot example)
assistant(few shot response)
...
user(query)
```
called with no tool calls and parsed using Koog's structured output feature.
The user however can override the default `execute` lambda. (Does this also make demonstrations work as expected? Does it work with multiple LLM calls?)

Avoid mutation for easy parallelization (in MIPRO, we can evaluate in parallel)

## Optimization Config
Holds one optimization parameter configuration per optimizableNode

# Strategy and node descriptions
TODO Explain

## Constraints on Koog GraphAIAgent node
One node = one LLM call (?)
Output has to be work with Koog structued output (i.e. JSON serializable and schemable)


# Open Questions
## 
- GraphAiAgent = Strategy + PromptExecutor + ToolSetRegistry

- Trace collection: Vibe-coded `TraceCollectionFeature` to intercept NodeExecutionCompleted

- In OptimizableNode, can we subsume instruction and demonstrations somehow?

- hook into subgraph primitive somehow?

# BUG!!!
We rely on optimizable node names / ids to be unique
Check whether this is also true for vanilla Koog nodes
Fix: Make optimizableNOde hashable and use that as key


# Input Output Fields
Provide labeled examples at the point of strategy constructions via the demonstrations field

# Get rid of LabeledFewShot
Only thing it is doing: Shuffling and coercing demonstrations.
Make sure that teacher also gets assigned those shuffled and coerced examples.

