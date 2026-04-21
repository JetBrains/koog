# Koog Framework Java Example

A demonstration project showcasing the [Koog Framework](https://koog.ai) for building AI agents with Spring Boot and Java.

## Overview

This project demonstrates how to build intelligent AI agents using the Koog framework in a Java/Spring Boot environment. The example implements a customer support system that uses multiple AI models (OpenAI GPT-4, Anthropic Claude) to handle order-related queries with automated problem identification, resolution, and verification.

## Features

- **Multi-LLM AI Agent System**: Leverages multiple language models (OpenAI GPT-4, Claude Sonnet) for different subtasks
- **Functional Agent Strategy**: Implements a sophisticated agent workflow with:
  - Problem identification
  - Automated resolution
  - Multi-step verification loop
- **Tool-based Architecture**: Uses annotated Java methods as AI-callable tools
- **RESTful API**: Spring Boot REST endpoints for agent interaction
- **Async Agent Execution**: Non-blocking agent execution with state monitoring
- **Event Handling**: Built-in event hooks for tool calls and agent lifecycle

## Architecture

### Core Components

1. **KoogAgentService** (`src/main/java/org/example/koog/java/agents/KoogAgentService.java`):
   - Creates and manages AI agents
   - Implements a three-stage agent workflow:
     - Stage 1: Identify user's problem (GPT-4o)
     - Stage 2: Solve the problem (Claude Sonnet 4)
     - Stage 3: Verify solution (GPT-o3) with retry loop
   - Tracks agents by user ID

2. **ApiController** (`src/main/java/org/example/koog/java/endpoints/ApiController.java`):
   - `POST /api/support`: Launch a new support agent
   - `GET /api/agents`: List all agents for a user
   - `GET /api/agents/{id}/status`: Get agent execution status

3. **UserTools** (`src/main/java/org/example/koog/java/tools/UserTools.java`):
   - Annotated tool methods callable by AI agents:
     - `readUserOrders()`: Retrieve user orders
     - `readUserAccount()`: Get account information
     - `issueRefund(orderId)`: Process refunds
     - `makeAnotherOrder(items)`: Create new orders

4. **Data Structures** (`src/main/java/org/example/koog/java/structs/`):
   - `OrderSupportRequest`: Structured problem identification
   - `OrderUpdateSummary`: Resolution results
   - `UserAccount`, `OrderInfo`, `Item`: Domain models

## Getting Started

### Prerequisites

- Java 17 or higher
- Gradle
- API keys for:
  - OpenAI (GPT-4, GPT-o3)
  - Anthropic (Claude Sonnet)

### Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd koog-java-exp-01
```

2. Configure Koog version in `gradle.properties`:
```properties
koogVersion=<version>
```

3. Set up API keys in your environment or application configuration

4. Build the project:
```bash
./gradlew build
```

5. Run the application:
```bash
./gradlew bootRun
```

## Usage Example

### Launch a Support Agent

```bash
curl -X POST http://localhost:8080/api/support \
  -H "Content-Type: application/json" \
  -d '{"question": "My order 12345 has not arrived yet"}'
```

Response:
```json
{
  "agentId": "agent-uuid-here"
}
```

### Check Agent Status

```bash
curl http://localhost:8080/api/agents/agent-uuid-here/status
```

Response examples:
- `"Agent is running..."`
- `"Agent finished with result: OrderUpdateSummary(orderId=12345, summary='Refund issued')"`

### List User Agents

```bash
curl http://localhost:8080/api/agents
```

## How It Works

The agent follows a sophisticated workflow:

1. **Problem Identification**: Uses GPT-4o with read-only tools to understand the user's issue and extract structured information
2. **Problem Resolution**: Uses Claude Sonnet 4 with full tool access to take actions (refunds, new orders, etc.)
3. **Verification Loop**: Uses GPT-o3 to verify the solution actually addresses the original problem
4. **Retry on Failure**: If verification fails, uses Claude Sonnet 4.5 to retry the resolution with feedback

This demonstrates Koog's powerful features:
- **Subtasks**: Breaking complex agent logic into focused subtasks
- **Dynamic LLM Selection**: Using different models for different stages
- **Tool Scoping**: Restricting tools per subtask
- **Verification**: Built-in solution validation
- **Type Safety**: Structured inputs/outputs with Java classes

## Key Dependencies

- **Koog Framework**:
  - `ai.koog:koog-spring-boot-starter`
  - `ai.koog:koog-agents-jvm`
  - `ai.koog:agents-features-sql`
- **Spring Boot 4.0.0-SNAPSHOT**
- **Kotlin support** for Koog interop

## Project Structure

```
src/main/java/org/example/koog/java/
├── KoogJavaApplication.java       # Spring Boot entry point
├── agents/
│   └── KoogAgentService.java      # Agent creation and management
├── endpoints/
│   └── ApiController.java         # REST API endpoints
├── structs/                       # Data models
│   ├── OrderSupportRequest.java
│   ├── OrderUpdateSummary.java
│   ├── UserAccount.java
│   └── ...
└── tools/                         # AI-callable tools
    ├── UserTools.java
    └── utils/
```

## Advanced Features (TODO)

The project includes commented-out code for additional features:

- **Persistence**: Automatic agent state persistence with rollback support
- **OpenTelemetry**: Distributed tracing and observability
- **Rollback Tools**: Automatic action reversal on agent failure

## License

See LICENSE file for details.

## Resources

- [Koog Framework Documentation](https://docs.koog.ai)
- [Koog Website](https://koog.ai)

---

Built with [Koog](https://koog.ai) - The AI Agent Framework
