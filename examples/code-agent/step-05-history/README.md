# Code Agent - Step 05: History Compression

A code agent with history compression that handles long-running tasks without hitting token limits.

## Prerequisites

- Java 17+
- Anthropic API key (main agent)
- OpenAI API key (for history compression and find sub-agent)
- (Optional) Langfuse credentials for observability

## Setup

```bash
export ANTHROPIC_API_KEY=your_anthropic_key
export OPENAI_API_KEY=your_openai_key
```

Optional: For Langfuse observability
```bash
export LANGFUSE_PUBLIC_KEY=your_public_key
export LANGFUSE_SECRET_KEY=your_secret_key
export LANGFUSE_HOST=https://langfuse.labs.jb.gg
export LANGFUSE_SESSION_ID=your_session_id
```

Optional: Auto-approve shell commands (use with care)
```bash
export BRAVE_MODE=true
```

## Run

Navigate to this example:
```
cd examples/code-agent/step-05-history
```

Run the agent on any project:
```
./gradlew run --args="/absolute/path/to/project 'Task description'"
```

Example:
```
./gradlew run --args="/Users/yourname/my-project 'Add error handling'"
```
