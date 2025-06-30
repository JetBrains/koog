# Testing Guidelines

When making changes in the project, please make sure that the corresponding tests are not failing.

## Running unit tests

This project uses Kotlin Multiplatform with JVM tests located in the `jvmTest` source sets.

### Running all JVM tests

To run all JVM tests in the project:

```bash
./gradlew jvmTest
```

### Running tests from a specific module

To run JVM tests from a specific module:

```bash
./gradlew :<module>:jvmTest
```

For example, to run JVM tests from the agents-test module:

```bash
./gradlew :agents:agents-test:jvmTest
```

### Running a specific test class

To run a specific test class:

```bash
./gradlew :<module>:jvmTest --tests "fully.qualified.TestClassName"
```

For example:

```bash
./gradlew :agents:agents-test:jvmTest --tests "ai.koog.agents.test.SimpleAgentMockedTest"
```

## Running integration tests

Integration tests are located in the `integration-tests` module and are used to test interactions with external LLM
services.

### Running all integration tests

To run all integration tests in the project:

```bash
./gradlew jvmIntegrationTest
```

### Running integration tests from a specific module

To run integration tests from a specific module:

```bash
./gradlew :<module>:jvmIntegrationTest
```

For example, to run integration tests from the integration-tests module:

```bash
./gradlew :integration-tests:jvmIntegrationTest
```

### Running a specific integration test

Integration test methods are prefixed with `integration_` to distinguish them from unit tests. To run a specific
integration test:

```bash
./gradlew :<module>:jvmIntegrationTest --tests "fully.qualified.TestClassName.integration_testMethodName"
```

For example:

```bash
./gradlew :integration-tests:jvmIntegrationTest --tests "ai.koog.integration.tests.SingleLLMPromptExecutorIntegrationTest.integration_testExecute"
```

### Required API tokens for integration tests

Integration tests that interact with LLM services require API tokens to be set as environment variables:

- `ANTHROPIC_API_TEST_KEY` - Required for tests using Anthropic's Claude models
- `OPEN_AI_API_TEST_KEY` - Required for tests using OpenAI's models
- `GEMINI_API_TEST_KEY` - Required for tests using Google's Gemini models
- `OPEN_ROUTER_API_TEST_KEY` - Required for tests using OpenRouter

You need to set these environment variables before running the integration tests that use the corresponding LLM clients.
To simplify development, you can also create `env.properties` file (already gitignored) using [env.template.propertes](./integration-tests/env.template.properties) as a template.
Then properties specified there would be automatically applied as environment variables when you run any test task.

## Running Ollama tests

Ollama tests are integration tests that use the Ollama LLM client. These tests are located in the `integration-tests`
module and are prefixed with `ollama_` to distinguish them from other integration tests.

### Running Ollama tests with jvmOllamaTest

To run all Ollama tests in the project:

```bash
./gradlew jvmOllamaTest
```

To run Ollama tests from a specific module:

```bash
./gradlew :integration-tests:jvmOllamaTest
```

### Running a specific Ollama test

To run a specific Ollama test:

```bash
./gradlew :integration-tests:jvmOllamaTest --tests "fully.qualified.TestClassName.ollama_testMethodName"
```

For example:

```bash
./gradlew :integration-tests:jvmOllamaTest --tests "ai.koog.integration.tests.OllamaClientIntegrationTest.ollama_test execute simple prompt"
```

### Requirements for running Ollama tests

#### Option 1: Using Docker (default)

By default, Ollama tests use a Docker container to run the Ollama server. You need to:

1. Have Docker installed and running on your machine
2. Set the `OLLAMA_IMAGE_URL` environment variable to the URL of the Ollama image to use

For example:

```bash
export OLLAMA_IMAGE_URL="ollama/ollama:latest"
```

#### Option 2: Using a local Ollama client

Alternatively, you can use a local Ollama client:

1. Install Ollama from [https://ollama.com/download](https://ollama.com/download)
2. Pull the required model (e.g., `llama3`)
3. Comment out the `@field:InjectOllamaTestFixture` annotation in the test class
4. Manually specify the executor and model in the test

Example of modifying a test to use a local Ollama client:

```kotlin
// @ExtendWith(OllamaTestFixtureExtension::class)
class OllamaClientIntegrationTest {
    companion object {
        // @field:InjectOllamaTestFixture  // Comment out this line
        private lateinit var fixture: OllamaTestFixture

        // Manually initialize executor and model
        private val executor = SingleLLMPromptExecutor(OllamaClient("http://localhost:11434"))
        private val model = OllamaModels.Meta.LLAMA_3_2
    }

    // Test methods...
}
```

Note that when using a local Ollama client, you need to ensure that the model specified in the test is pulled and
available in your local Ollama installation.