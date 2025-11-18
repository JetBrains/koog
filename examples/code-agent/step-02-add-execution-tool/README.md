# Code Agent - Step 01: Minimal Agent

> Code from the blog post: [Building AI Agents in Kotlin – Part 2: A Deeper Dive Into Tools]()

A minimal code agent with three tools that can navigate codebases and make targeted changes.

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
cd examples/code-agent/step-01-minimal-agent
```

Run the agent on any project:
```
./gradlew run --args="/absolute/path/to/project 'Task description'"
```

Example:
```
./gradlew run --args="/Users/yourname/my-project 'Add error handling'"
```
