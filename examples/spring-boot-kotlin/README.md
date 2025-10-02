# Spring Boot Kotlin Example

A Spring Boot application with Koog AI agent, providing a REST API endpoint for chat interactions using Google's models.

## Features

- REST API for chat interactions
- Supported Google LLMs: gemini-2.0-flash, gemini-2.0-flash-001, gemini-2.0-flash-lite, gemini-2.0-flash-lite-001, gemini-2.5-pro, gemini-2.5-flash, gemini-2.5-flash-lite
- Configurable prompts and MCP server tools via `application.yml` (a GitHub assistant by default)
- Koog Agent Persistence feature on AWS S3 (disabled by default)

## Prerequisites

- Java 17 or higher
- Docker
- Gradle 8.14 (there is also `pom.xml.example` if you prefer Maven)
- Google API key
- GitHub personal access token with corresponding permissions
- Optional: AWS credentials to try Agent Persistence feature on AWS S3

## Running the Application

1. Set your Google API key: `export GOOGLE_API_KEY=your_google_key`
2. Set your GitHub personal access token: `export GITHUB_PERSONAL_ACCESS_TOKEN=your_github_pat`
3. Optional: to try Agent Persistence feature edit agent.s3_persistence properties and set your AWS credentials: `export AWS_ACCESS_KEY_ID=your_aws_key_id` and `export AWS_SECRET_ACCESS_KEY=your_aws_secret_key`
4. Run Docker 
5. Navigate to directory: `cd examples/spring-boot-kotlin`
6. Run: `./gradlew bootRun`

Application starts on `http://localhost:8080`.

## API Usage

### Chat Endpoint

Send a POST request to interact with the AI:

**Endpoint:** `POST http://localhost:8080/chat`

**Content-Type:** `application/json`

**Request Body:**
```json
{
  "prompt": "List the last three commits in the GitHub repository your_username/your_repo and summarize them."
}
```

**Sample response:**
```json
{
  "response": "Here's a summary of the last three commits in the your_username/your_repo repository:\n\n1.  **Create script.sh**: This commit created a shell script file named `script.sh`.\n2.  **Initial commit**: This is the initial commit, likely setting up the basic structure of the repository.\n\n"
}
```

### Example using cURL

```bash
curl -v -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "List the last three commits in the GitHub repository your_username/your_repo and summarize them."}' | jq
```

## Key Components

- **`ChatController.kt`**: REST endpoint handling chat requests
- **`AgentService.kt`**: Creates an AI agent and tools according to YAML configuration.
- **`AgentConfiguration.kt`**: Data classes corresponding to YAML configuration.

## Customization

- **Change AI persona**: Modify system prompt in `application.yml`
- **Use different models**: Change LLM in `application.yml` via agent.model.id.
- **Configure your own tools for an AI agent**: Modify or add MCP servers in `application.yml`

## Troubleshooting

- **API key errors**: Set `GOOGLE_API_KEY` environment variable
- **Permission issues**: Double-check the permissions of the GitHub personal access token in `GITHUB_PERSONAL_ACCESS_TOKEN` environment variable
- **Startup issues**: Ensure Java 17+ and proper `JAVA_HOME`
- **Runtime issues**: Ensure that Docker process is running
- **API errors**: Check console logs for details
