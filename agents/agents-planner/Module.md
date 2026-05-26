# Module agents-planner

Provides planner strategy implementations for `agents-core`.

The main entry point is [**Planners**](src/commonMain/kotlin/ai/koog/agents/planner/Planners.kt),
which exposes factory methods for all built-in planner types:

- **GOAP** (`Planners.goap`) — Goal-Oriented Action Planning. Defines a state machine of actions and goals;
  the planner finds the cheapest action sequence that satisfies a goal condition.
  Built with a DSL via `GOAPPlannerBuilder` (package `goap`), or the top-level `goap { }` Kotlin extension for a more concise syntax.
- **LLM-based** (`Planners.llmBased`, `Planners.llmBasedWithCritic`) — delegates planning to an LLM.
  The critic variant adds a second LLM pass to evaluate and refine the generated plan.
  Built via `SimpleLLMPlannerBuilder` (package `llm`).
