# Glossary

## Agent

- **Agent**: an AI entity that can interact with tools, handle complex workflows, and communicate with
  users.

- **LLM (Large Language Model)**: the underlying AI model that powers agent capabilities.

- **Message**: a unit of communication in the agent system that represents data passed from a user, assistant, or system.

- **Prompt**: the conversation history provided to an LLM that consists of messages from a user, assistant, and system.

- **System prompt**: instructions provided to an agent to guide its behavior, define its role, and supply key information necessary for its tasks.

- **Context**: the environment in which LLM interactions occur, with access to the conversation history and
  tools.

- **LLM session**: a structured way to interact with LLMs that includes the conversation history, available tools,
  and methods to make requests.

## Agent workflow

- **Strategy**: a defined workflow for an agent that consists of sequential subgraphs.
The strategy defines how the agent processes input, interacts with tools, and generates output.
A strategy graph consists of nodes connected by edges that represent transitions between nodes.

### Strategy graphs

- **Graph**: a structure of nodes connected by edges that defines an agent strategy workflow.

- **Node**: a fundamental building block of an agent strategy workflow that represents a specific operation or transformation.

- **Edge**: a connection between nodes in an agent graph that defines the flow of operations, often with conditions
  that specify when to follow each edge.

- **Conditions**: rules that determine when to follow a particular edge.

- **Subgraph**: a self-contained unit of processing within an agent strategy, with its own set of tools, context, and
responsibilities. Information about subgraph operations can be either encapsulated within the subgraph or transferred between
subgraphs using the AgentMemory feature.

## Tools

- **Tool**: a function that an agent can use to perform specific tasks or access external systems. The agent is aware of the
available tools and their arguments but lacks knowledge of their implementation details.

- **Tool call**: a request from an LLM to run a specific tool using the provided arguments. It functions similarly to a function call.

- **Tool descriptor**: tool metadata that includes its name, description, and parameters.

- **Tool registry**: a list of tools available to an agent. The registry informs the agent about the available tools.

- **Tool result**: an output produced by running a tool. For example, if the tool is a method, the result would be its return value.

## History compression

- **History compression**: the process of reducing the size of the conversation history to manage token usage by applying various compression strategies.
To learn more, see [History compression](history-compression.md).

## Features

- **Feature**: a component that extends and enhances the functionality of AI agents.

### EventHandler feature

- **EventHandler**: a feature that enables monitoring and responding to various agent events, providing hooks for tracking agent lifecycle, handling errors, and processing tool invocations 
  throughout the workflow.

### AgentMemory feature

- **AgentMemory**: a feature that enables AI agents to store, retrieve, and use information across conversations. To learn more, see [AgentMemory](agent-memory.md).

- **Concept**: a category of information with associated metadata in the AgentMemory feature, including a keyword,
description, and fact type. Concepts are fundamental building blocks of the AgentMemory system that the agent can remember and recall.
To learn more, see [AgentMemory](agent-memory.md).

- **Fact**: an individual piece of information stored in the AgentMemory system.
Facts are associated with concepts and can either have a single value or multiple values.
To learn more, see [AgentMemory](agent-memory.md).

- **Memory scope**: the context in which facts are relevant. To learn more, see [AgentMemory](agent-memory.md).
