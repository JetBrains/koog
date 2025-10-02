# Agent-to-Agent (A2A) Examples

Examples demonstrating the A2A protocol for inter-agent communication with standardized message/task workflows, streaming responses, and artifact delivery.

## Examples

### Simple Joke Agent (`simplejoke/`)

Basic message-based communication without tasks.

**Run:**
```bash
# Terminal 1: Start server (port 9998)
./gradlew runExampleSimpleJokeServer

# Terminal 2: Run client
./gradlew runExampleSimpleJokeClient
```

### Advanced Joke Agent (`advancedjoke/`)

Task-based workflow with:
- Interactive clarification (InputRequired state)
- Artifact delivery for results
- Graph-based agent strategy with documented nodes/edges
- Streaming response events

**Run:**
```bash
# Terminal 1: Start server (port 9999)
./gradlew runExampleAdvancedJokeServer

# Terminal 2: Run client
./gradlew runExampleAdvancedJokeClient
```

## Key Patterns

**Simple Agent:** `sendMessage()` → single response
**Advanced Agent:** `sendMessageStreaming()` → Flow of events (Task, TaskStatusUpdateEvent, TaskArtifactUpdateEvent)

**Task States:** Submitted → Working → InputRequired (optional) → Completed

See code comments in `JokeWriterAgentExecutor.kt` for detailed flow documentation.
