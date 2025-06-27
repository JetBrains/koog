# Memory

## Feature overview

The AgentMemory feature is a component of the Koog framework that lets AI agents store, retrieve, and use
information across conversations.

### Purpose

The AgentMemory Feature addresses the challenge of maintaining context in AI agent interactions by:

- Storing important facts extracted from conversations.
- Organizing information by concepts, subjects, and scopes.
- Retrieving relevant information when needed in future interactions.
- Enabling personalization based on user preferences and history.

### Architecture

The AgentMemory feature is built on a hierarchical structure.
The elements of the structure are listed and explained in the sections below.

#### Facts 

***Facts*** are individual pieces of information stored in the memory. 
Facts represent actual stored information.
There are two types of facts:

- **SingleFact**: a single value associated with a concept. For example, an IDE user's current preferred theme:
```kotlin
// Storing favorite IDE theme (single value)
val themeFact = SingleFact(
    concept = Concept(
        "ide-theme", 
        "User's preferred IDE theme", 
        factType = FactType.SINGLE),
    value = "Dark Theme",
    timestamp = DefaultTimeProvider.getCurrentTimestamp()
)
```
- **MultipleFacts**: multiple values associated with a concept. For example, all languages that a user knows:
```kotlin
// Storing programming languages (multiple values)
val languagesFact = MultipleFacts(
    concept = Concept(
        "programming-languages",
        "Languages the user knows",
        factType = FactType.MULTIPLE
    ),
    values = listOf("Kotlin", "Java", "Python"),
    timestamp = DefaultTimeProvider.getCurrentTimestamp()
)
```

#### Concepts 

***Concepts*** are categories of information with associated metadata.

- **Keyword**: unique identifier for the concept.
- **Description**: detailed explanation of what the concept represents.
- **FactType**: whether the concept stores single or multiple facts (`FactType.SINGLE` or `FactType.MULTIPLE`).

#### Subjects

***Subjects*** are entities that facts can be associated with.

Common examples of subjects include:

- **User**: Personal preferences and settings
- **Environment**: Information related to the environment of the application

There is a predefined `MemorySubject.Everything` that you may use as a default subject for all facts.
In addition, you can define your own custom memory subjects by extending the `MemorySubject` abstract class:

```kotlin
object MemorySubjects {
    /**
     * Information specific to the local machine environment
     * Examples: Installed tools, SDKs, OS configuration, available commands
     */
    @Serializable
    data object Machine : MemorySubject() {
        override val name: String = "machine"
        override val promptDescription: String =
            "Technical environment (installed tools, package managers, packages, SDKs, OS, etc.)"
        override val priorityLevel: Int = 1
    }
}
```
#### Scopes 

***Memory scopes*** are contexts in which facts are relevant:

- **Agent**: specific to an agent.
- **Feature**: specific to a feature.
- **Product**: specific to a product.
- **CrossProduct**: relevant across multiple products.

## Configuration and initialization

The feature integrates with the agent pipeline through the `AgentMemory` class, which provides methods for saving and
loading facts, and can be installed as a feature in the agent configuration.

### Configuration

The `AgentMemory.Config` class is the configuration class for the AgentMemory feature.

```kotlin
class Config : FeatureConfig() {
    var memoryProvider: AgentMemoryProvider = NoMemory
    var scopesProfile: MemoryScopesProfile = MemoryScopesProfile()

    var agentName: String
    var featureName: String
    var organizationName: String
    var productName: String
}
```

### Installation

To install the AgentMemory feature in an agent, follow the pattern provided in the code sample below.

```kotlin
val agent = AIAgent(...) {
    install(AgentMemory) {
        memoryProvider = memoryProvider
        agentName = "your-agent-name"
        featureName = "your-feature-name"
        organizationName = "your-organization-name"
        productName = "your-product-name"
    }
}
```

## Examples and quickstarts

### Basic usage

The following code snippets demonstrate the basic setup of a memory storage and how facts are saved to and loaded from
the memory.

1. Set up memory storage
```kotlin
// Create a memory provider
val memoryProvider = LocalFileMemoryProvider(
    config = LocalMemoryConfig("customer-support-memory"),
    storage = SimpleStorage(JVMFileSystemProvider.ReadWrite),
    fs = JVMFileSystemProvider.ReadWrite,
    root = Path("path/to/memory/root")
)
```

2. Store a fact in the memory
```kotlin
memoryProvider.save(
    fact = SingleFact(
        concept = Concept("greeting", "User's name", FactType.SINGLE),
        value = "John",
        timestamp = DefaultTimeProvider.getCurrentTimestamp()
    ),
    subject = MemorySubject.User
)
```
3. Retrieve the fact
```kotlin
// Get the stored information
try {
    val greeting = memoryProvider.load(
        concept = Concept("greeting", "User's name", FactType.SINGLE),
        subject = MemorySubjects.User
    )
    println("Retrieved: $greeting")
} catch (e: MemoryNotFoundException) {
    println("Information not found. First time here?")
} catch (e: Exception) {
    println("Error accessing memory: ${e.message}")
}
```

#### Using memory nodes

The AgentMemory feature provides the following predefined memory nodes that can be used in agent strategies:

* [nodeLoadAllFactsFromMemory](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.feature.nodes/node-load-all-facts-from-memory.html): loads all facts about the subject from the memory for a given concept.
* [nodeLoadFromMemory](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.feature.nodes/node-load-from-memory.html): loads specific facts from the memory for a given concept.
* [nodeSaveToMemory](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.feature.nodes/node-save-to-memory.html): saves a fact to the memory.
* [nodeSaveToMemoryAutoDetectFacts](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.feature.nodes/node-save-to-memory-auto-detect-facts.html): automatically detects and extracts facts from the chat history and saves them to the memory. Uses the LLM to identify concepts.

Here is an example of how nodes can be implemented in an agent strategy:

```kotlin
val strategy = strategy("example-agent") {
        // Node to automatically detect and save facts
        val detectFacts by nodeSaveToMemoryAutoDetectFacts<Unit>(
            subjects = listOf(MemorySubjects.User, MemorySubjects.Project)
        )

        // Node to load specific facts
        val loadPreferences by node<Unit, Unit> {
            withMemory {
                loadFactsToAgent(
                    concept = Concept("user-preference", "User's preferred programming language", FactType.SINGLE),
                    subjects = listOf(MemorySubjects.User)
                )
            }
        }

        // Connect nodes in the strategy
        edge(nodeStart forwardTo detectFacts)
        edge(detectFacts forwardTo loadPreferences)
        edge(loadPreferences forwardTo nodeFinish)
}
```

#### Making memory secure

You can use encryption to make sure that sensitive information is protected inside an encrypted storage used by the
memory provider.

```kotlin
// Simple encrypted storage setup
val secureStorage = EncryptedStorage(
    fs = JVMFileSystemProvider.ReadWrite,
    encryption = Aes256GCMEncryption("your-secret-key")
)
```

#### Example: Remembering user preferences

Here is an example of how AgentMemory is used in a real-world scenario to remember a user's preference, specifically
the user's favorite programming language.

```kotlin
memoryProvider.save(
    fact = SingleFact(
        concept = Concept("preferred-language", "What programming language is preferred by the user?", FactType.SINGLE),
        value = "Kotlin",
        timestamp = DefaultTimeProvider.getCurrentTimestamp()
    ),
    subject = MemorySubjects.User
)
```

### Advanced usage

#### Custom nodes with memory

You can also use the memory from the `withMemory` clause inside any node. The ready-to-use `loadFactsToAgent` and `saveFactsFromHistory` higher level abstractions save facts to the history, load facts from it, and update the LLM chat:

```kotlin
val loadProjectInfo by node<Unit, Unit> {
    withMemory {
        loadFactsToAgent(Concept("project-structure", ...))
    }
}

val saveProjectInfo by node<Unit, Unit> {
    withMemory {
        saveFactsFromHistory(Concept("project-structure", ...))
    }
}
```

#### Automatic fact detection

You can also ask the LLM to detect all the facts from the agent's history using the `nodeSaveToMemoryAutoDetectFacts` method:

```kotlin
val saveAutoDetect by nodeSaveToMemoryAutoDetectFacts<Unit>(
    subjects = listOf(MemorySubjects.User, MemorySubjects.Project)
)
```
In the example above, the LLM would search for the user-related facts and project-related facts, determine the concepts, and save them into the memory.

## Best practices

1. **Start Simple**
    - Begin with basic storage without encryption
    - Use single facts before moving to multiple facts

2. **Organize Well**
    - Use clear concept names
    - Add helpful descriptions
    - Keep related information under the same subject

3. **Handle Errors**
   ```kotlin
   try {
       memoryProvider.save(fact, subject)
   } catch (e: Exception) {
       println("Oops! Couldn't save: ${e.message}")
   }
   ```
   For more details on error handling, see [Error handling and edge cases](#error-handling-and-edge-cases).

## Error handling and edge cases

The AgentMemory feature includes several mechanisms to handle edge cases:

1. **NoMemory provider**: a default implementation that doesn't store anything, used when no memory provider is
   specified.

2. **Subject specificity handling**: when loading facts, the feature prioritizes facts from more specific subjects
   based on their defined `priorityLevel`.

3. **Scope filtering**: facts can be filtered by scope to ensure only relevant information is loaded.

4. **Timestamp tracking**: facts are stored with timestamps to track when they were created.

5. **Fact type handling**: the feature supports both single facts and multiple facts, with appropriate handling for each type.

## API documentation

For a complete API reference related to the AgentMemory feature, see the reference documentation for the [agents-features-memory](https://api.koog.ai/agents/agents-features/agents-features-memory/index.html) module.

API documentation for specific packages:

- [ai.koog.agents.local.memory.feature](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.feature/index.html): includes the `AgentMemory` class and the core implementation of the
  AI agents memory feature.
- [ai.koog.agents.local.memory.feature.nodes](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.feature.nodes/index.html): includes predefined memory-related nodes that can be used in
  subgraphs.
- [ai.koog.agents.local.memory.config](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.config/index.html): provides definitions of memory scopes used for memory operations.
- [ai.koog.agents.local.memory.model](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.model/index.html): includes definitions of the core data structures and interfaces
  that enable agents to store, organize, and retrieve information across different contexts and time periods.
- [ai.koog.agents.local.memory.feature.history](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.feature.history/index.html): provides the history compression strategy for retrieving and
  incorporating factual knowledge about specific concepts from past session activity or stored memory.
- [ai.koog.agents.local.memory.providers](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.providers/index.html): provides the core interface that defines the fundamental operation for storing and retrieving knowledge in a structured, context-aware manner and its implementations.
- [ai.koog.agents.local.memory.storage](https://api.koog.ai/agents/agents-features/agents-features-memory/ai.koog.agents.local.memory.storage/index.html): provides the core interface and specific implementations for file operations across different platforms and storage backends.

## FAQ and troubleshooting

### How do I implement a custom memory provider?

To implement a custom memory provider, create a class that implements the `AgentMemoryProvider` interface:

```kotlin
class MyCustomMemoryProvider : AgentMemoryProvider {
    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        // Implementation for saving facts
    }

    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        // Implementation for loading facts by concept
    }

    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        // Implementation for loading all facts
    }

    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> {
        // Implementation for loading facts by description
    }
}
```

### How are facts prioritized when loading from multiple subjects?

Facts are prioritized based on subject specificity. When loading facts, if the same concept has facts from multiple subjects, the fact from the most specific subject will be used.

### Can I store multiple values for the same concept?

Yes, by using the `MultipleFacts` type. When defining a concept, set its `factType` to `FactType.MULTIPLE`:

```kotlin
val concept = Concept(
    keyword = "user-skills",
    description = "Programming languages the user is skilled in",
    factType = FactType.MULTIPLE
)
```

This lets you store multiple values for the concept, which is retrieved as a list.
