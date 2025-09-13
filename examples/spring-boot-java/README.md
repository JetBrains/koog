# Spring Boot Java Example with Koog AI

A Spring Boot application that integrates Koog AI capabilities, providing a REST API endpoint for chat interactions using OpenAI's GPT models.

## Features

- REST API for chat interactions with reactive programming
- OpenAI GPT-4 integration via Koog
- Configurable AI persona (helpful pirate by default)

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- OpenAI API key

## Running the Application

1. Set your OpenAI API key: `export OPENAI_API_KEY=your_key`
2. Navigate to directory: `cd examples/spring-boot-java`
3. Run: `mvn spring-boot:run`

Application starts on `http://localhost:8080`.

## API Usage

### Chat Endpoint

Send a POST request to interact with the AI:

**Endpoint:** `POST http://localhost:8080/api/chat`

**Content-Type:** `application/json`

**Request Body:**
```json
{
  "message": "Tell me a joke"
}
```

**Response:**
```json
{
  "response": "Ahoy! Why don't pirates ever say goodbye? Because they always say 'sea you later!' Arrr!"
}
```

### Example using cURL

```bash
curl -v -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Tell me a joke"}' | jq
```

### Example using HTTPie

```bash
http POST localhost:8080/api/chat message="Tell me a joke"
```

## Key Components

- **`ChatController.java`**: REST endpoint handling chat requests
- **`AIService.java`**: Integrates with Koog's prompt executor, uses GPT-4-1-nano model
- **Configuration**: OpenAI API key via `OPENAI_API_KEY` environment variable

## Customization

- **Change AI persona**: Modify system prompt in `AIService.java`
- **Use different models**: Change model in `AIService.java` 
- **Adjust parameters**: Modify `LLMParams` object for temperature, max tokens, etc.

## Troubleshooting

- **API key errors**: Set `OPENAI_API_KEY` environment variable
- **Startup issues**: Ensure Java 17+ and proper `JAVA_HOME`
- **API errors**: Check console logs for details
