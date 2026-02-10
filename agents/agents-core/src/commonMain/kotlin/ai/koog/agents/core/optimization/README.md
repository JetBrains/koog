# Outline
-Koog Graph Strategy ~ dspy program
- Koog GraphAIAgent node ~ dsyp predictor

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

Trace collection: Vibe-coded `TraceCollectionFeature` to intercept NodeExecutionCompleted

## OptimizableNode' demonstrations
The demonstrations given to optimizableNode during strategy construction are used as labeled few shot examples.
In the original dspy implementation, this is handled by the `LabeledFewShot` class. We also need machinery to link examples in training data to the right nodes.
Our implementation does away with that in order to simplify the API and DSL.

## Optimization Config
Holds one optimization parameter configuration per optimizableNode

## Strategy and node descriptions
A natural text description of the strategy and each node is used by the InstructionProposer to generate candidate instructions.
In the dspy implementation, this description is generated via LLM from the source code.
In our Koog implementation, we allow the user to provide a description for the strategy and each node.
Would LLMs struggle a bit more to generate descriptions from Koog DSL and Kotlin code, as opposed to Python code?  

## Constraints on optimizable Koog GraphAIAgent node
One node = one LLM call (?)
Output has to be work with Koog structued output (i.e. JSON serializable and schemable)

## InstructionProposer prompts
The instructino proposer prompts right now closely follow the original DSPY implementation.
Perhaps we want to adapt them? Also there are tons of choices being made here, perhaps this could/should be exposed to the user?

# Open Questions
## Alternative: substrategy ~ predictor ?
Modify subgraph primitive, or use subgraph with task thingy?

## Assemble the optimized agent
- GraphAiAgent = Strategy + PromptExecutor + ToolSetRegistry

## In OptimizableNode, can we subsume instruction and demonstrations somehow?

## Make optimizableNode's extra field generalizable
What if our optimization space is not given in terms of instructions, demosntrations?
Make it easy to e.g. optimize over model choice (perhaps even per-node)

# BUG!!!
We rely on optimizable node names / ids to be unique
Check whether this is also true for vanilla Koog nodes
Fix: Make optimizableNOde hashable and use that as key

## Input Output Fields (Removed)
Provide labeled examples at the point of strategy constructions via the demonstrations field

## Get rid of LabeledFewShot (Done)
Only thing it is doing: Shuffling and coercing demonstrations.
Make sure that teacher also gets assigned those shuffled and coerced examples.

