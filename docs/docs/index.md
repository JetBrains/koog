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

## Agent types

<div class="grid cards" markdown>

-   :material-robot-outline:{ .lg .middle } [**Basic agents**](basic-agents.md)

    ---

    Create and run agents that process a single input and provide a response

-   :material-script-text-outline:{ .lg .middle } [**Functional agents**](functional-agents.md)

    ---

    Create and run lightweight agents with custom logic in plain Kotlin 

-   :material-graph-outline:{ .lg .middle } [**Complex workflow agents**](complex-workflow-agents.md)

    ---

    Create and run agents that handle complex workflows with custom strategies

</div>

## Core functionality

<div class="grid cards" markdown>

-   :material-chat-processing-outline:{ .lg .middle } [**Prompts**](prompt-api.md)

    ---

    Create prompts, run them using LLM clients or prompt executors,
    switch between LLMs and providers, and handle failures with built-in retries

-   :material-wrench:{ .lg .middle } [**Tools**](tools-overview.md)

    ---

    Enhance your agents with built‑in, annotation‑based, or class‑based tools
    that can access external systems and APIs

-   :material-share-variant-outline:{ .lg .middle } [**Strategies**](predefined-agent-strategies.md)

    ---

    Design complex agent behaviors using intuitive graph-based workflows

-   :material-bell-outline:{ .lg .middle } [**Events**](agent-events.md)

    ---

    Monitor and process agent lifecycle, strategy, node, LLM call, and tool call events with predefined handlers

</div>

## Advanced usage

<div class="grid cards" markdown>

-   :material-history:{ .lg .middle } [**History compression**](history-compression.md)

    ---

    Optimize token usage while maintaining context in long-running conversations using advanced techniques

-   :material-state-machine:{ .lg .middle } [**Agent persistence**](agent-persistence.md)

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
