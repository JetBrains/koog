# TODO: update with the latest API version
# Module agents-planner

Library for implementing planning capabilities in AI agents.

## Overview

The agents-planner module provides components for creating AI agents that can plan and execute multi-step tasks. It
builds upon the core agent architecture to enable agents to:

1. Create plans with multiple steps
2. Execute plan steps sequentially
3. Evaluate plan progress and effectiveness
4. Replan when necessary

Key features include:

- Abstract planning strategy framework
- Simple planning implementation using LLM to create the plan
- Simple planning implementation with a critic component to evaluate the plan quality
- GOAP planner implementation, with automatic planning based on available actions and desired goals

## Core Concepts

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
)

// Create an agent with the planner
val agent = AIAgent(
    promptExecutor = llmExecutor,
    toolRegistry = toolRegistry,
    strategy = planner,
)

// Run the agent with a task
val result = agent.run("Create a marketing plan for a new product launch")
```
