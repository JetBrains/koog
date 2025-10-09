# Overview

Koog is an open-source JetBrains framework designed to build and run AI agents entirely in idiomatic Kotlin.
It lets you create agents that can interact with tools, handle complex workflows, and communicate with users.

<div class="grid cards" markdown>

-   :material-rocket-launch:{ .lg .middle } [Getting started](getting-started.md)

    ---

    Build and run your first AI agent

-   :material-book-open-variant:{ .lg .middle } [Glossary](glossary.md)

    ---

    Learn the essential terms

</div>

## Agents

<div class="grid cards" markdown>

-   :material-robot-outline:{ .lg .middle } [Single-run agents](single-run-agents.md)

    ---

    Create and run agents that process a single input and provide a response

-   :material-script-text-outline:{ .lg .middle } [Functional agents](functional-agents.md)

    ---

    Create and run lightweight agents with custom logic in plain Kotlin 

-   :material-graph-outline:{ .lg .middle } [Complex workflow agents](complex-workflow-agents.md)

    ---

    Create and run agents that handle complex workflows with custom strategies

</div>

## Core functionality

<div class="grid cards" markdown>

-   :material-chat-processing-outline:{ .lg .middle } [Prompt API](prompt-api.md)

    ---

    Create prompts, run them using LLM clients or prompt executors,
    switch between LLMs and providers, and handle failures with built-in retries

-   :material-wrench:{ .lg .middle } [Tools](tools-overview.md)

    ---

    Enhance your agents with built‑in, annotation‑based, or class‑based tools
    that can access external systems and APIs

-   :material-share-variant-outline:{ .lg .middle } [Agent strategies](predefined-agent-strategies.md)

    ---

    Design complex agent behaviors using intuitive graph-based workflows

-   :material-bell-outline:{ .lg .middle } [Agent events](agent-events.md)

    ---

    Monitor and process agent lifecycle, strategy, node, LLM call, and tool call events with predefined handlers

</div>

## Advanced features

<div class="grid cards" markdown>

-   :material-history:{ .lg .middle } [History compression](history-compression.md)

    ---

    Optimize token usage while maintaining context in long-running conversations using advanced techniques

-   :material-state-machine:{ .lg .middle } [Agent persistence](agent-persistence.md)

    ---

    Restore the agent state at specific points during execution
        

-   :material-code-braces:{ .lg .middle } [Structured output](structured-output.md)

    ---

    Generate responses in structured formats

-   :material-waves:{ .lg .middle } [Streaming API](streaming-api.md)

    ---

    Process responses in real-time with streaming support and parallel tool calls

-   :material-puzzle-outline:{ .lg .middle } [Features](features-overview.md)

    ---

    Customize your agent capabilities through a composable architecture

</div>

## Integrations

<div class="grid cards" markdown>

-   :material-puzzle:{ .lg .middle } [Model Context Protocol (MCP)](model-context-protocol.md)

    ---

    Use MCP tools directly in AI agents

-   :material-leaf:{ .lg .middle } [Spring Boot](spring-boot.md)

    ---

    Add Koog to your Spring applications

-   :material-cloud-outline:{ .lg .middle } [Ktor](ktor-plugin.md)

    ---

    Integrate Koog with Ktor servers

-   :material-chart-timeline-variant:{ .lg .middle } [OpenTelemetry](opentelemetry-support.md)

    ---

    Trace, log, and measure your agent with popular observability tools

-   :material-lan:{ .lg .middle } [A2A Protocol](a2a-protocol-overview.md)

    ---

    Connect agents and services over a shared protocol

</div>
