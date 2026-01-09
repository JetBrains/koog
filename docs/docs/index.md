# Overview

Koog is an open-source JetBrains framework for building AI agents with an idiomatic, type-safe Kotlin DSL designed specifically for JVM and Kotlin developers.
It lets you create agents that interact with tools, handle complex workflows, and communicate with users.

You can customize agent capabilities with a modular feature system and deploy your agents across JVM, JS, WasmJS, Android, and iOS targets using Kotlin Multiplatform.

<div class="grid cards" markdown>

-   :material-rocket-launch:{ .lg .middle } [**Getting started**](getting-started.md)

    ---

    Build and run your first AI agent

-   :material-book-open-variant:{ .lg .middle } [**Glossary**](glossary.md)

    ---

    Learn the essential terms

</div>

## Agents

Learn about [agents in general](agents/index.md) and how to create different types of agents using Koog:

<div class="grid cards" markdown>

-   :material-robot-outline:{ .lg .middle } [**Basic agents**](agents/basic-agents.md)

    ---

    Use a predefined strategy that works for most common use cases

-   :material-function:{ .lg .middle } [**Functional agents**](agents/functional-agents.md)

    ---

    Define custom logic as a lambda function in plain Kotlin 

-   :material-state-machine:{ .lg .middle } [**Graph-based agents**](agents/graph-based-agents.md)

    ---

    Implement a custom workflow as a strategy graph

-   :material-list-status:{ .lg .middle } [**Planner agents**](agents/planner-agents.md)

    ---

    Iteratively build and execute a plan until the state matches the desired conditions

</div>

## Core components

Learn about the core components of Koog agents in detail:

<div class="grid cards" markdown>

-   :material-chat-processing-outline:{ .lg .middle } [**Prompts**](prompts/index.md)

    ---

    Create, manage, and run prompts that drive the agent's interaction with the LLM

-   :material-strategy:{ .lg .middle } [**Strategies**](predefined-agent-strategies.md)

    ---

    Design the agent's intended workflow as a directed graph

-   :material-tools:{ .lg .middle } [**Tools**](tools-overview.md)

    ---

    Enable the agent to interact with external data sources and services

-   :material-toy-brick-outline:{ .lg .middle } [**Features**](features-overview.md)

    ---

    Extend and enhance the functionality of AI agents


</div>

## Advanced usage

<div class="grid cards" markdown>

-   :material-history:{ .lg .middle } [**History compression**](history-compression.md)

    ---

    Optimize token usage while maintaining context in long-running conversations using advanced techniques

-   :material-floppy:{ .lg .middle } [**Agent persistence**](agent-persistence.md)

    ---

    Restore the agent state at specific points during execution
        

-   :material-code-braces:{ .lg .middle } [**Structured output**](structured-output.md)

    ---

    Generate responses in structured formats

-   :material-waves:{ .lg .middle } [**Streaming API**](streaming-api.md)

    ---

    Process responses in real-time with streaming support and parallel tool calls

-   :material-database-search:{ .lg .middle } [**Knowledge retrieval**](embeddings.md)

    ---

    Retain and retrieve knowledge across conversations using [vector embeddings](embeddings.md), [ranked document storage](ranked-document-storage.md), and [shared agent memory](agent-memory.md)

-   :material-timeline-text:{ .lg .middle } [**Tracing**](tracing.md)

    ---

    Debug and monitor agent execution with detailed, configurable tracing

</div>

## Integrations

<div class="grid cards" markdown>

-   :material-puzzle:{ .lg .middle } [**Model Context Protocol (MCP)**](model-context-protocol.md)

    ---

    Use MCP tools directly in AI agents

-   :material-leaf:{ .lg .middle } [**Spring Boot**](spring-boot.md)

    ---

    Add Koog to your Spring applications

-   :material-cloud-outline:{ .lg .middle } [**Ktor**](ktor-plugin.md)

    ---

    Integrate Koog with Ktor servers

-   :material-chart-timeline-variant:{ .lg .middle } [**OpenTelemetry**](opentelemetry-support.md)

    ---

    Trace, log, and measure your agent with popular observability tools

-   :material-lan:{ .lg .middle } [**A2A Protocol**](a2a-protocol-overview.md)

    ---

    Connect agents and services over a shared protocol

</div>
