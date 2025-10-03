# Code Agents Memory Feature

A beginner-friendly guide to adding memory capabilities to AI agents.

## Table of Contents 📑
- [What is Memory and Why Do We Need It?](#what-is-memory-and-why-do-we-need-it-)
- [Understanding Memory Through Real-World Examples](#understanding-memory-through-real-world-examples-)
- [Core Concepts Made Simple](#core-concepts-made-simple-)
  - [Facts and Concepts](#1-facts-and-concepts)
  - [Memory Organization](#2-memory-organization)
- [Getting Started (Super Simple Example)](#getting-started-super-simple-example-)
  - [Set Up Memory Storage](#step-1-set-up-memory-storage)
  - [Store Your First Fact](#step-2-store-your-first-fact)
  - [Retrieve the Fact](#step-3-retrieve-the-fact)
  - [Memory Maintenance](#step-4-memory-maintenance)
- [Making Memory Secure](#making-memory-secure-)
- [Common Use Cases](#common-use-cases-)
- [Best Practices for Beginners](#best-practices-for-beginners-)
- [Advanced Features](#advanced-features-when-youre-ready-)
- [Need Help?](#need-help-)
- [License](#license-)

## What is Memory and Why Do We Need It? 🤔

Imagine you're talking to a smart assistant. Without memory, every time you chat, it's like meeting a stranger - they don't remember anything from your previous conversations! That's why we need memory.

This module gives AI agents the ability to remember things, just like humans do. Think of it as a smart notebook where agents can:
- Write down important information (storing facts)
- Remember it later when needed (retrieving facts)
- Share notes with other agents (knowledge sharing)
- Keep sensitive information safe (encryption)

## Understanding Memory Through Real-World Examples 📚

### Example 1: Personal Assistant
Imagine a personal assistant who needs to:
- Remember your preferred IDE settings
- Know which programming languages you use
- Recall your commonly used commands

Without memory, they'd have to ask you these things every single time!

### Example 2: Project Helper
Think of a helper that:
- Remembers your project's structure
- Knows your build system configuration
- Keeps track of your coding style preferences

Just like a experienced team member who knows all the project details!

## Core Concepts Made Simple 🎯

### 1. Facts and Concepts
Think of it like organizing a library:
- **Concept** = Category (like "Programming Languages" or "IDE Settings")
- **Fact** = Actual information (like "Java 17" or "Dark Theme")

Facts come in two types:
1. **Single Facts** (like your name - you only have one)
   ```kotlin
   // Storing favorite IDE theme (single value)
   val themeFact = SingleFact(
       concept = Concept("ide-theme", "User's preferred IDE theme"),
       value = "Dark Theme"
   )
   ```

2. **Multiple Facts** (like your skills - you can have many)
   ```kotlin
   // Storing programming languages (multiple values)
   val languagesFact = MultipleFact(
       concept = Concept(
           "programming-languages",
           "Languages the user knows",
           factType = FactType.MULTIPLE  // Important: specify MULTIPLE type
       ),
       values = listOf("Kotlin", "Java", "Python")
   )
   ```

### 2. Memory Organization
Just like organizing files in folders:

#### Subjects (Where to Store)
Think of subjects as main folders for different types of information. Common examples include:
- **User**: Personal preferences and settings
- **Environment**: Information related to the environment of the application

And there is a pre-defined `MemorySubject.Everything` that you may use as a default subject for all facts.

You can define your own custom memory subjects by extending the `MemorySubject` abstract class:

```kotlin
@Serializable
data object CustomSubject : MemorySubject() {
    override val name: String = "custom"
    override val promptDescription: String = "Custom information specific to my use case"
    override val priorityLevel: Int = 5  // Lower numbers mean higher priority
}
```

#### Scopes (Who Can Access)
Scopes control who can see and use the stored information:
- **Agent**: Only the specific AI agent can access it
- **Feature**: Shared between agents of the same feature
- **Product**: Available across the entire product
- **CrossProduct**: Shared between different products

```kotlin
// Example 1: Storing user-specific IDE theme
memoryProvider.save(
    fact = themeFact,
    subject = MemorySubjects.User,        // Personal preference
    scope = MemoryScope.Product("my-ide") // Available in this IDE
)

// Example 2: Storing project build system
memoryProvider.save(
    fact = buildSystemFact,
    subject = MemorySubjects.Project,     // Project information
    scope = MemoryScope.Feature("build")  // Only for build-related features
)
```

## Getting Started (Super Simple Example) 🚀

### Step 1: Set Up Memory Storage
```kotlin
// Create basic memory storage
val memoryProvider = LocalFileMemoryProvider(
    config = LocalMemoryConfig("my-first-memory"),
    storage = SimpleStorage(JVMFileSystemProvider),
    root = Path("memory/data")
)
```

### Step 2: Store Your First Fact
```kotlin
// Store a simple fact
memoryProvider.save(
    fact = SingleFact(
        concept = Concept("greeting", "User's name", FactType.SINGLE),
        value = "John"
    ),
    subject = MemorySubject.User
)
```

### Step 3: Retrieve the Fact
```kotlin
// Get the stored information
val greetingFacts = memoryProvider.load(
    concept = Concept("greeting", "User's name", FactType.SINGLE),
    subject = MemorySubject.User
)

if (greetingFacts.isEmpty()) {
    println("Information not found. First time here?")
} else {
    // For single facts, typically you'd access the first (and only) element
    val greeting = (greetingFacts.first() as SingleFact).value
    println("Retrieved: $greeting")
}
```

### Step 4: Memory Maintenance
```kotlin
// Clean up old or unused facts
memoryProvider.cleanup {
    // Remove facts older than 30 days
    olderThan(timeProvider.getCurrentTimestamp() - 30.days)
    // Or remove specific concepts
    withConcept("temporary-data")
}

// Compact storage to save space
memoryProvider.compact()
```

## Making Memory Secure 🔒

When dealing with sensitive information, you can use encryption:

```kotlin
// Simple encrypted storage setup
val secureStorage = EncryptedStorage(
    fs = JVMFileSystemProvider,
    encryption = Aes256GCMEncryption("your-secret-key")
)
```

## Examples 💡

1. **Remembering User Preferences**
   ```kotlin
   // Store user's favorite programming language
   memoryProvider.save(
       fact = SingleFact(
           concept = Concept("preferred-language", "What programming language is preferred by the user?", FactType.SINGLE),
           value = "Kotlin"
       ),
       subject = MemorySubjects.User
   )
   ```

## Best Practices for Beginners 📝

1. **Start Simple**
   - Begin with basic storage without encryption
   - Use single facts before moving to multiple facts
   - Stick to USER and PROJECT subjects initially

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

## Advanced Features (When You're Ready) 🎓

Once you're comfortable with the basics, you can explore:

# Memory in Agents
## Adding Memory Feature to Agents
Just use `install(AgentMemory)` and configure it as you like when creating the agent:
```kotlin
val myAgent = AIAgents(
    ...
) {
    install(AgentMemory) {
        memoryProvider = myMemoryProvider // see above how to create one!

        /** 
         * `featureName` will be used to link your agent to
         * others via shared memory when using 
         * `MemoryScope.FEATURE` :
        */
        featureName = "test-feature"

        /**
         * `organizationName` will be used to link your agent to
         * others via shared memory when using
         * `MemoryScope.ORGANIZATION` :
         */
        organizationName = "test-organization"
    }
}
```

## Memory Usage in Agent Strategy Graphs
### Loading facts from memory
Let's assume that we have the following concepts:
```kotlin
val projectStructureConcept = Concept("project-structure", description = "Structure of the project, including modules, folders, important files with code, etc.", FactType.MULTIPLE)
val dependenciesConcept = Concept("project-dependencies", description = "Dependencies of the project in build system. Please, output dependencies indicating corresponding project modules, versions, and all the details", FactType.MULTIPLE)
```
and following memory subjects:
```kotlin
/**
 * Information specific to the current user
 * Examples: Preferences, settings, authentication tokens
 */
@Serializable
data object User : MemorySubject() {
    override val name: String = "user"
    override val promptDescription: String = "User's preferences, settings, and behavior patterns, expectations from the agent, preferred messaging style, etc."
    override val priorityLevel: Int = 2
}

/**
 * Information specific to the current project
 * Examples: Build configuration, dependencies, code style rules
 */
@Serializable
data object Project : MemorySubject() {
    override val name: String = "project"
    override val promptDescription: String = "Project details, requirements, and constraints, dependencies, folders, technologies, modules, documentation, etc."
    override val priorityLevel: Int = 3
}
```
There are some ready-to made nodes available.
For example, loading from memory (usually when starting your agent) can be done like this:
```kotlin
val loadFromMemory by nodeLoadFromMemory(concepts = listOf(projectStructureConcept, dependenciesConcept))
```
The `nodeLoadFromMemory` would load all the facts about the given concepts from the memory storage (configured for the agent when installing the `AgentMemory`), and write them into the LLM chat as context.

Note: when using `nodeLLMCompressHistory()` by default, all memory facts would persist in the history. If you want to also remove them (or compress using the LLM), please provide `preserveMemory` parameter: `nodeLLMCompressHistory(preserveMemory = false)`.

At the end of your agent's execution, you would probably want to save some facts into the memory. You can decide whether they will be saved on the `AGENT`, `FEATURE`, `PRODUCT`, or `ORGANIZATION` scope (when loading, other agents might decide to only read the memory from the same agent or within the same feature, or for the same project. `ORGANIZATION` scope allows all agents to share some information across different products of the company). Also, you may select whether the concept concerns the machine (ex: operating system), user (ex: preferences), project (ex: dependencies), or organization

### Saving facts to memory
```kotlin
val saveDependenciesToMemory by nodeSaveToMemory(
    dependenciesConcept, 
    subject = MemorySubjects.Project, 
    scope = MemoryScopeType.ORGANIZATION
)

val saveProjectStructureToMemory by nodeSaveToMemory(
    projectStructureConcept, 
    subject = MemorySubjects.Project, 
    scope = MemoryScopeType.PRODUCT
)
```
In the example above:
- project dependencies were saved for the current project (only agents working with the same project will see this information) and on an organization scope (all agents in all products -- IDEs, TeamCity, you name it -- will be able to load this concept).
- project structure was saved for the current project but on a product scope (visible only for agents working in the same product, ex: the same IDE).

### Automatic facts detection
You can also ask the LLM to detect all the facts from the agent's history using the `nodeSaveToMemoryAutoDetectFacts` method:
```kotlin
val saveAutoDetect by nodeSaveToMemoryAutoDetectFacts<Unit>(
    subjects = listOf(MemorySubjects.User, MemorySubjects.Project)
)
```
In the example above, LLM would search for the user-related facts and project-related facts, determine the concepts, and save them into memory.

### Custom nodes with memory (advanced usage)
You can also use memory from `withMemory` clause inside any node. You will find the ready-to made `loadFactsToAgent` and `saveFactsFromHistory` higher level abstractions (that load/save facts from the history and update the LLM chat):  
```kotlin
val loadProjectInfo by node {
    withMemory {
        loadFactsToAgent(Concept("project-structure", ...))
    }
}

val saveProjectInfo by node {
    withMemory {
        saveFactsFromHistory(Concept("project-structure", ...))
    }
}
```
Or you can use `memoryFeature` with mannual `load` and `save` that work with `Fact` (you have to retrieve the facts by yourself into a `List<Fact>` and save them manually):
```kotlin
val saveUserLikesRedColor by node {
    withMemory {
        memoryFeature.save(
            fact = SingleFact(
                concept = Concept("favorite-color", description = "TODO"),
                values = "Red",
                timestamp = getCurrentTimestamp()
            ),
            subject = MemorySubjects.User,
            scope = MemoryScopeType.ORGANIZATION
        )
    }
}
```

### Advanced Querying
```kotlin
// Query facts by multiple criteria
val facts = memoryProvider.query {
    withConcept("build-system")
    withSubject(MemorySubjects.Project)
    withTimestamp(after = timeProvider.getCurrentTimestamp() - 24.hours)
}
```

For detailed documentation on these features, check the following resources in the repository:

## Need Help? 🆘

- **Examples and Documentation**:
  - Check the `examples` directory for working code examples
  - Browse the `docs` directory for detailed guides
  - Look at implementation tests for usage patterns

- **Best Practices**:
  - Start with the examples in this README
  - Follow the code patterns in the test directory
  - Review the implementation tests for advanced usage

Remember: Start small, experiment, and gradually build up to more complex use cases!
