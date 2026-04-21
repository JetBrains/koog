# Code Agent - Step 03: Add Observability

> Code from the blog post: [TBA]()

This example extends our Code Agent from Step 2 with OpenTelemetry observability through LangFuse.

## Prerequisites

- Java 17+
- OpenAI API key

## Setup

```bash
export OPENAI_API_KEY="your-openai-key"
export LANGFUSE_HOST="https://cloud.langfuse.com"
export LANGFUSE_PUBLIC_KEY="your-public-key"
export LANGFUSE_SECRET_KEY="your-secret-key"
```

## Run

Navigate to this example:
```
cd examples/code-agent/step-03-add-observability
```

Run the agent on any project:
```
./gradlew run --args="/absolute/path/to/project 'Task description'"
```
