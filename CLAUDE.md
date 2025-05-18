# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository contains the Koan Agents framework, a Kotlin multiplatform library for building AI agents. The framework enables creating intelligent agents that interact with tools, handle complex workflows, and maintain context across conversations.

## Building and Testing

### Basic Commands

```bash
# Build the project
./gradlew assemble

# Compile test classes
./gradlew jvmTestClasses jsTestClasses

# Run all JVM tests
./gradlew jvmTest

# Run all JS tests
./gradlew jsTest

# Run a specific test class
./gradlew jvmTest --tests "fully.qualified.TestClassName"
# Example:
./gradlew jvmTest --tests "ai.koog.agents.test.SimpleAgentIntegrationTest"

# Run a specific test method
./gradlew jvmTest --tests "fully.qualified.TestClassName.testMethodName"
# Example:
./gradlew jvmTest --tests "ai.koog.agents.test.SimpleAgentIntegrationTest.simpleChatAgent should call default tools"
```

## Architecture

### Key Modules

1. **agents-core**: Core abstractions and interfaces
   - AIAgent, AIAgentStrategy, event handling system, AIAgent, execution strategies, session management

2. **agents-tools**: Tool infrastructure
   - Tool, ToolRegistry, ToolDescriptor

3. **agents-features**: Extensible agent capabilities
   - Memory, tracing, and other features 

4. **prompt**: LLM interaction layer
   - LLM executors, prompt construction, structured data processing

### Core Concepts

- **Agents**: State-machine graphs with nodes that process inputs and produce outputs
- **Tools**: Encapsulated actions with standardized interfaces
- **Strategies**: Define agent behavior and execution flow
- **Features**: Installable extensions that enhance agent capabilities
- **Event Handling**: System for intercepting and processing agent lifecycle events

### Implementation Pattern

1. Define tools that agents can use
2. Register tools in the ToolRegistry
3. Configure agent with strategy
4. Set up communication (if integrating with external systems)

## Testing

The project has extensive testing support:

- **Mocking LLM responses**:
  ```kotlin
  val mockLLMApi = getMockExecutor(toolRegistry, eventHandler) {
      mockLLMAnswer("Hello!") onRequestContains "Hello"
      mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
  }
  ```

- **Mocking tool calls**:
  ```kotlin
  mockTool(PositiveToneTool) alwaysReturns "The text has a positive tone."
  ```

- **Testing agent graph structure**:
  ```kotlin
  testGraph {
      assertStagesOrder("first", "second")
      // ...
  }
  ```

For detailed testing guidelines, refer to `agents/TESTING.md`.