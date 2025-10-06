# Delivery support AI agent for E-commerce app. Koog + Spring Boot + Kotlin

> Disclaimer: this README file was generated with Junie.

A sample Kotlin Spring Boot application demonstrating how to build an interactive customer-support AI agent using Koog framework.
The service exposes simple REST endpoints to launch an AI agent that can assist with order-related questions, track state and checkpoints, 
and perform rollbacks using Koog's Persistence feature.

## Description of the application

This app provides a minimal REST API to:
- Launch a support agent for a user that handles a natural-language question (/api/support)
- List all agent IDs created for the user (/api/agents)
- Check the current status of a specific agent (/api/agents/{id}/status)
- List checkpoints captured during the agent run (/api/agent/{id}/checkpoints)
- Roll back the given agent to a specific checkpoint (/api/agents/{id}/rollback/{checkpointId})

Key behaviors:
- Agents are created per user and execute concurrently.
- OpenTelemetry integration enables tracing of agent execution.
- Koog snapshot Persistence stores agent checkpoints (configured for SQL/Postgres in this example).
- Rollbacks are wired via a rollback registry so that critical tool actions can be undone.

Relevant implementation files:
- src/main/kotlin/ai/koog/spring/sandwich/KoogSpringSandwichApp.kt — Spring Boot entry point
- src/main/kotlin/ai/koog/spring/sandwich/endpoints/ApiController.kt — REST API endpoints
- src/main/kotlin/ai/koog/spring/sandwich/agents/KoogAgentService.kt — Agent creation, execution, checkpoints, rollback
- src/main/resources/application.yml — Configuration (LLM API keys, logging, Spring)

## Used Koog components

This project showcases several Koog modules and features:

- ai.koog:koog-spring-boot-starter — Spring Boot integration for Koog
- ai.koog:koog-agents — Core agent runtime (AIAgent, tools, strategies)
  - AIAgent with PromptExecutor and model selection (OpenAIModels.Chat.GPT5Nano)
  - ToolRegistry with example tool sets (CommunicationTools, OrderTools, UserTools)
- ai.koog:agents-features-sql — Snapshot persistence support (checkpoints storage via SQL/Postgres)
- OpenTelemetry feature — Tracing integration for agent runs

See KoogAgentService.kt for how these pieces are wired:
- install(OpenTelemetry) to configure tracing and span exporters
- install(Persistence) to enable automatic checkpoints and rollback registry
- RollbackToolRegistry to connect forward actions with compensating rollback functions

## Prerequisites

- JDK 17+
- Gradle (wrapper included)
- Network access to LLM providers (e.g., OpenAI)
- Optional: a Postgres instance if you want to persist checkpoints (adjust storage in checkpoints module)

Environment variables (from application.yml):
- OPENAI_API_KEY — OpenAI key (default: sk-test)
- ANTHROPIC_API_KEY — Anthropic key (default: sk-test)

## How to build

Using the Gradle wrapper:

- macOS/Linux:
  - ./gradlew clean build
- Windows:
  - gradlew.bat clean build

The build will compile the application and run tests.

## How to launch

Run with Gradle:
- macOS/Linux: ./gradlew bootRun
- Windows: gradlew.bat bootRun

Or run the jar:
1. ./gradlew clean bootJar
2. java -jar build/libs/koog-spring-sandwich-0.0.1-SNAPSHOT.jar

By default, the application reads configuration from src/main/resources/application.yml.
Ensure OPENAI_API_KEY and other required environment variables are set before launching.

## API quick start

Assuming the app runs locally (default Spring Boot port 8080) and you authenticate the Principal as needed (for demos, you can adapt security config to provide a test Principal):

- Launch agent:
  POST http://localhost:8080/api/support
  Body: { "question": "Where is my order?" }
  Response: { "agentId": "..." }

- List agents for current user:
  GET http://localhost:8080/api/agents

- Agent status:
  GET http://localhost:8080/api/agents/{id}/status

- List checkpoints:
  GET http://localhost:8080/api/agent/{id}/checkpoints

- Roll back to a checkpoint:
  PUT http://localhost:8080/api/agents/{id}/rollback/{checkpointId}

Note: Endpoints rely on a valid Principal.name as userId. For local development, set up a simple security configuration or a mock principal approach to test the flow.

## Configuration

Edit src/main/resources/application.yml for:
- ai.koog.openai.api-key / ai.koog.anthropic.api-key
- logging levels
- Spring application name and build info import
- OpenTelemetry metrics export step

Checkpoint storage: see src/main/kotlin/ai/koog/spring/sandwich/checkpoints for Postgres-backed persistence helper (createPostgresStorage).

## Koog website

Learn more about Koog: https://koog.ai

