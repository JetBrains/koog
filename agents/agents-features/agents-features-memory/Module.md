# Module agents-features-memory

Provides `AgentMemory` feature that allows to store and persist facts from LLM history between agent runs and even
between multiple agents

### Overview

The agents-features-memory module provides memory capabilities for AI agents, allowing them to store, retrieve, and share information between conversations and even between different agents. This enables agents to maintain context, remember user preferences, and build knowledge over time.

Key features include:
- Storage and retrieval of facts with concepts and values
- Memory organization with subjects and scopes
- Secure storage with encryption options
- Memory sharing between agents
- Automatic fact detection from agent history

### Using in your project

To use the memory feature in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-features-memory:$version")
}
```

Then, install the AgentMemory feature when creating your agent:

```kotlin
val myAgent = AIAgents(
    // other configuration parameters
) {
    install(AgentMemory) {
        memoryProvider = LocalFileMemoryProvider(
            config = LocalMemoryConfig("my-memory"),
            storage = SimpleStorage(JVMFileSystemProvider),
            root = Path("memory/data")
        )
        featureName = "my-feature"
        organizationName = "my-organization"
    }
}
```

### Using in unit tests

For testing agents with memory capabilities, you can use an in-memory storage implementation:

```kotlin
// Create an in-memory storage for testing
val testMemoryProvider = LocalFileMemoryProvider(
    config = LocalMemoryConfig("test-memory"),
    storage = SimpleStorage(InMemoryFileSystemProvider),
    root = Path("test/memory")
)

// Create a test agent with memory
val testAgent = AIAgents(
    // other test configuration
) {
    install(AgentMemory) {
        memoryProvider = testMemoryProvider
        featureName = "test-feature"
    }

    // Enable testing mode
    withTesting()
}
```

This approach allows you to test memory operations without writing to the actual file system.

### Example of usage

Here's an example of using memory in an agent strategy graph:

```kotlin
// Define concepts for storing information
val projectStructureConcept = Concept(
    "project-structure", 
    "Structure of the project, including modules and important files", 
    FactType.MULTIPLE
)

val userPreferencesConcept = Concept(
    "user-preferences", 
    "User's preferred settings and configurations", 
    FactType.SINGLE
)

// Create a strategy with memory operations
val strategy = strategyGraph<Unit, String> {
    // Node to load facts from memory
    val loadFromMemory by nodeLoadFromMemory(
        concepts = listOf(projectStructureConcept, userPreferencesConcept)
    )

    // Node to process user request
    val processRequest by nodeLLM<Unit, String> {
        // LLM processing logic
    }

    // Node to save facts to memory
    val saveProjectInfo by nodeSaveToMemory(
        projectStructureConcept,
        subject = MemorySubjects.Project,
        scope = MemoryScopeType.PRODUCT
    )

    // Connect the nodes
    start - loadFromMemory - processRequest - saveProjectInfo - end
}
```

This example demonstrates loading facts from memory, processing a request, and saving updated information back to memory.
