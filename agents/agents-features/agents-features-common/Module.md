# Module agents-features-common

Provides common infrastructure and utilities for implementing agent features, including configuration, messaging, and I/O capabilities.

### Overview

The agents-features-common module serves as the foundation for all agent feature modules, providing shared infrastructure components and utilities. It includes a robust messaging system, configuration framework, and I/O utilities that enable features to communicate, process events, and persist data.

Key components include:
- Feature configuration framework for managing feature settings
- Message and event system for feature communication
- Thread-safe file, log, and remote writers for output
- Client/server architecture for remote communication
- Utility classes for exception handling and concurrency

### Using in your project

To use the common feature infrastructure in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-features-common:$version")
}
```

When implementing a custom feature, you can extend the base classes provided by this module:

```kotlin
class MyFeatureConfig : FeatureConfig() {
    // Custom configuration properties
}

class MyFeatureMessageProcessor : FeatureMessageProcessor() {
    override suspend fun processMessage(message: FeatureMessage) {
        // Custom message processing logic
    }
}
```

### Using in unit tests

For testing features that use the common infrastructure, you can create mock implementations:

```kotlin
// Create a test configuration
val testConfig = object : FeatureConfig() {
    // Test-specific configuration
}

// Create a test message processor that captures messages
class TestMessageProcessor : FeatureMessageProcessor() {
    val messages = mutableListOf<FeatureMessage>()

    override suspend fun processMessage(message: FeatureMessage) {
        messages.add(message)
    }
}

// Add the test processor to your configuration
val processor = TestMessageProcessor()
testConfig.addMessageProcessor(processor)

// Verify messages after test execution
assertEquals(expectedMessage, processor.messages.first())
```

### Example of usage

Here's an example of implementing a custom feature using the common infrastructure:

```kotlin
// Define a custom feature configuration
class LoggingFeatureConfig : FeatureConfig() {
    var logLevel: String = "INFO"
}

// Create a custom message processor
class LoggingMessageProcessor : FeatureMessageProcessor() {
    override suspend fun processMessage(message: FeatureMessage) {
        when (message) {
            is FeatureStringMessage -> println("[${message.timestamp}] ${message.message}")
            is FeatureEventMessage -> println("[${message.timestamp}] Event: ${message.eventId}")
        }
    }
}

// Install the feature in your agent
val agent = AIAgents(/* configuration */) {
    install(LoggingFeature) {
        logLevel = "DEBUG"
        addMessageProcessor(LoggingMessageProcessor())
    }
}
```

This example demonstrates how to create a custom feature with configuration and message processing capabilities using the common infrastructure.
