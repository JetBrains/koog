# Module agents-features-memory

Provides the `ChatMemory` feature that persists and restores conversation history between agent runs.

### Overview

The `agents-features-memory` module enables AI agents to maintain continuity across multiple sessions by
storing and loading the full conversation history. When a new agent run starts, the previous messages are
automatically injected into the prompt; when the run completes, the updated history is saved back to the
configured provider.

### Main Components

- **ChatMemory** — the installable agent feature. Intercepts strategy start and completion events to load
  and store conversation history via a `ChatHistoryProvider`.
- **ChatMemoryConfig** — configuration holder. Accepts a `ChatHistoryProvider` implementation and an
  optional list of `ChatMemoryPreProcessor`s applied to the history before it is used or saved.
- **ChatHistoryProvider** — interface for reading and writing conversation history, keyed by a conversation
  (run) identifier. Implementations can target any storage backend (in-memory, SQL, file system, etc.).
- **InMemoryChatHistoryProvider** — built-in, thread-safe in-memory implementation of `ChatHistoryProvider`,
  useful for testing and short-lived sessions.
- **ChatMemoryPreProcessor** — interface for transforming the message list before it is injected into the
  prompt or persisted. The module ships with a sliding-window pre-processor that trims history to a
  configurable maximum number of messages.

### Goals

- Provide conversation continuity across agent runs without requiring changes to agent strategy logic.
- Keep the storage backend pluggable so teams can use any persistence layer.
- Allow history shaping (e.g. truncation, filtering) through a composable pre-processor pipeline.
