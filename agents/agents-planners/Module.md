# Module agents-planners

Library for implementing planning capabilities in AI agents.

## Overview

The agents-planners module provides components for creating AI agents that can plan and execute multi-step tasks. It
builds upon the core agent architecture to enable agents to:

1. Create plans with multiple steps
2. Execute plan steps sequentially
3. Evaluate plan progress and effectiveness
4. Replan when necessary

Key features include:

- Abstract planning strategy framework
- Simple planning implementation using LLM to create the plan
-

## Using in your project

To use the agents-planners module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-planners:$version")
}
```

## Core Concepts

### Plan

A `Plan` represents a sequence of steps to be executed by an agent to achieve a goal. It provides methods to:

- Check if the plan is completed
- Execute the next step in the plan

### PlanningAgentState

`PlanningAgentState` represents the current state of a planning agent, including:

- The agent context with access to resources
- The current value being processed
- The current plan (if any)

### PlanAssessment

`PlanAssessment` is used to evaluate a plan's execution and determine whether to:

- Continue with the current plan
- Replan with a specific reason

### PlanningAIAgentStrategy

`PlanningAIAgentStrategy` is an abstract class that implements the planning loop:

1. Build an initial plan
2. Execute steps until the plan is completed
3. After each step, evaluate the plan
4. Replan if necessary

## Implementations

### SimplePlanner

`SimplePlanner` is a basic implementation that uses LLM requests to:

- Build a structured plan with steps
- Execute each step sequentially
- Mark steps as completed

### SimplePlannerWithCritic

`SimplePlannerWithCritic` extends `SimplePlanner` by adding a critic component that:

- Evaluates the plan after each step
- Provides feedback on plan quality
- Recommends replanning when necessary

## Example of usage

```kotlin
// Create a simple planner
val planner = SimplePlanner(
    name = "TaskPlanner",
    toolSelectionStrategy = AllToolsStrategy()
)

// Create an agent with the planner
val agent = AIAgent(
    promptExecutor = llmExecutor,
    toolRegistry = toolRegistry,
    strategy = planner,
    eventHandler = eventHandler
)

// Run the agent with a task
val result = agent.execute("Create a marketing plan for a new product launch")
```
