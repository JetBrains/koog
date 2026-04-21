# Code Agent - Step 02: Add Execution Tool

> Code from the blog post: [Building AI Agents in Kotlin – Part 2: A Deeper Dive Into Tools]()

Extends the minimal agent with command execution capabilities, allowing the agent to run tests, build projects, and execute other shell commands.

## Prerequisites

- Java 17+
- OpenAI API key

## Setup

```bash
export OPENAI_API_KEY=your_openai_key
```

## Run

Navigate to this example:
```
cd examples/code-agent/step-02-add-execution-tool
```

Run the agent on any project:
```
./gradlew run --args="/absolute/path/to/project 'Task description'"
```

Example:
```
./gradlew run --args="/Users/yourname/my-project 'Add error handling'"
```
