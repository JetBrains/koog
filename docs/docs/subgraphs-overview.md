# Overview

This page provides detailed information about subgraphs in the Koog framework. Understanding these concepts is crucial for creating complex agent workflows that maintain context across multiple processing steps.

## Introduction

Subgraphs are a fundamental concept in the Koog framework that lets you break down complex agent workflows
into manageable, sequential steps. Each subgraph represents a phase of processing, with its context, responsibilities, and an optional subset of tools.

Subgraphs are integral parts of strategies, which are graphs that represent the overall agent workflow. For more information about strategies, see [Custom strategy graphs](custom-strategy-graphs.md).

## Understanding subgraphs

A subgraph is a self-contained unit of processing within an agent strategy. Each subgraph:

- Has a unique name
- Contains a graph of nodes or subgraphs connected by edges
- Can use any tool or a subset of tools from the tool registry
- Receives input from the previous subgraph (or the initial user input)
- Produces output that is passed to the next subgraph (or the output)

To define a sequence of subgraphs in a graph, use edge connections or define sequences using the `then` keyword. For
more information, see [Custom strategy graphs](custom-strategy-graphs.md).

### Subgraph context

Each subgraph executes within a context that provides access to:

- The environment
- Agent input
- The agent configuration
- The LLM context (including the conversation history)
- The state manager
- The storage
- Session and strategy

The context is passed to each node within the subgraph and provides the necessary resources for the node to perform its
operations.
