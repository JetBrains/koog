# A2A Module Development Guidelines

## Module Overview

The A2A (Agent-to-Agent) module is a **meta-module** within the larger Koog project that implements a comprehensive client and server library for the A2A protocol, a standardized communication protocol for AI agents based on the specification at https://a2a-protocol.org/latest/specification/.

**Important**: Since this is a meta-module inside a bigger root project, the Gradle wrapper executable is located one directory up. All Gradle commands must use `../gradlew` when running from the a2a directory.

### Purpose
- **Agent Discovery**: Through AgentCard manifests describing agent capabilities and interfaces
- **Message Exchange**: Standardized message format with support for different content types
- **Task Management**: Long-running operations with state tracking and lifecycle management
- **Push Notifications**: Asynchronous notifications for task updates
- **Multiple Transport Protocols**: JSON-RPC, HTTP+JSON/REST with extensibility for gRPC
- **Authentication & Security**: OpenAPI 3.0 compatible security schemes

### Submodules Architecture

1. **a2a-core**: Core abstractions, data models, and transport interfaces
   - AgentCard, Message, Task, Event hierarchy
   - Transport interfaces: ClientTransport, ServerTransport, RequestHandler
   - No external dependencies beyond Kotlin stdlib and serialization

2. **a2a-client**: High-level client library for communicating with A2A servers
   - A2AClient wrapper, AgentCardResolver
   - Capability validation, request/response handling
   - Depends on: a2a-core, Ktor HTTP client

3. **a2a-server**: Server-side implementation for hosting A2A agents
   - A2AServer, AgentExecutor interface, session management
   - Storage abstractions with in-memory implementations
   - Depends on: a2a-core, coroutines, logging

4. **a2a-transport**: Multiple transport protocol implementations
   - a2a-transport-core-jsonrpc: JSON-RPC protocol base classes
   - a2a-transport-client-jsonrpc-http: HTTP JSON-RPC client transport
   - a2a-transport-server-jsonrpc-http: HTTP JSON-RPC server transport
   - a2a-transport-*-rest: HTTP+JSON/REST protocol implementations

## Technologies & Libraries

### Core Dependencies (from gradle/libs.versions.toml)
- **Kotlin Multiplatform**
- **kotlinx-serialization**: JSON serialization for protocol messages
- **kotlinx-coroutines**: Async/concurrent programming, Flow APIs
- **kotlinx-datetime**: Timestamp handling in protocol messages
- **ktor3**: HTTP client/server for transport implementations
- **oshai-kotlin-logging**: Structured logging

### Testing Libraries
- **kotlin-test**: Multiplatform test framework
- **kotest-assertions**: Rich assertion library for complex objects
- **kotlinx-coroutines-test**: Testing coroutines with runTest
- **testcontainers**: Docker-based integration testing
- **logback-classic**: Runtime logging for tests

### Platform Support
- **JVM**: Full server and client support
- **JS (Browser)**: Client-only support
- **Future platforms**: Architecture ready for native support

## Development Guidelines

### Architecture Decisions
⚠️ **CRITICAL**: Never make design and architecture decisions independently. Always ask the user for confirmation before:
- Adding new transport protocols
- Changing storage interfaces
- Modifying core protocol message formats
- Adding new authentication schemes
- Changing session management behavior
- Doing other sorts of major architectural changes

### Code Style Conventions

#### API Visibility
- Use `explicitApi()` - all public APIs must have explicit visibility modifiers
- Prefer `public` declarations for APIs, `internal` for implementation details
- Use `@InternalA2AApi` annotation for APIs that are public but internal to the module

#### Naming Conventions
- Classes: PascalCase (`A2AServer`, `SessionManager`, `AgentExecutor`)
- Interfaces: Same as classes, often ending in -or/-er for behaviors (`AgentExecutor`)
- Functions: camelCase with descriptive names (`onSendMessage`, `getByContext`)
- Properties: camelCase (`agentCard`, `sessionMutex`)

#### Async Patterns
- Use `suspend` functions for all async operations
- Prefer `Flow` for streaming APIs (task events, message streams)
- Use `RWLock` pattern: `rwLock.withReadLock/withWriteLock` for concurrent access
- Use `Mutex` for single-threaded critical sections: `mutex.withLock`

#### Error Handling
- Use domain-specific exceptions (`A2AInternalErrorException`, `TaskOperationException`)
- Propagate errors properly in async contexts
- Include contextual information in exception messages

#### KDoc Documentation
- **Placement**: KDoc directly above declarations (classes, functions, properties)
- **Constructor properties**: Document using `@param` tags in class KDoc
- **Public class properties**: Document using `@property` tags in class KDoc
- **Cross-references**: Use `[ClassName]`, `[ClassName.propertyName]` syntax for linking components
- **Examples**: Include practical code examples for complex APIs
- **Validation rules**: Document constraints with bullet points and clear explanations
- **Exception documentation**: Use `@throws` with specific conditions
- **Required**: All public APIs must have KDoc (enforced by `explicitApi()`)

## Testing Requirements

### Mandatory Test Execution
⚠️ **ALWAYS** run the specific test suite using Gradle's jvmTest task to ensure changes work correctly:

```bash
# Run all tests in a specific module
../gradlew :a2a:a2a-server:jvmTest

# Run a specific test class
../gradlew :a2a:a2a-server:jvmTest --tests "ai.koog.a2a.server.tasks.InMemoryTaskStorageTest"

# Run a specific test method
../gradlew :a2a:a2a-client:jvmTest --tests "ai.koog.a2a.client.A2AClientIntegrationTest.test get agent card"
```

### Testing Approach: Crucial Minimum
- **Focus on core functionality** - test essential behavior, not implementation details
- **Separate dedicated tests** - one test method per core scenario/edge case
- **Avoid verbose, unnecessary tests** - don't test trivial getters/setters
- **Test error conditions** - verify proper exception handling and error states

#### Preferred Assertion Pattern
**✅ DO**: Assert on whole objects when possible (especially data classes):
```kotlin
assertEquals(expectedTask, actualTask)  // Compares all properties
assertEquals(listOf(expectedArtifact), retrieved?.artifacts)
```

**❌ AVOID**: Bunch of individual field assertions unless necessary:
```kotlin
// Avoid this pattern:
assertEquals(expected.id, actual.id)
assertEquals(expected.contextId, actual.contextId)
assertEquals(expected.status, actual.status)
// ... many more individual assertions
```

### Integration Tests
- **Docker-based testing**: a2a-client uses testcontainers with Python A2A server
- **Docker build dependency**: Integration tests automatically build required containers
- **Test isolation**: Each test should be independent and clean up after itself

### Test Structure Examples

**Unit Tests** (InMemoryTaskStorageTest pattern):
```kotlin
class InMemoryTaskStorageTest {
    private lateinit var storage: InMemoryTaskStorage

    @BeforeTest
    fun setUp() {
        storage = InMemoryTaskStorage()
    }

    @Test
    fun testStoreAndRetrieveTask() = runTest {
        val task = createTask(id = "task-1", contextId = "context-1")
        storage.update(task)
        val retrieved = storage.get("task-1")

        assertNotNull(retrieved)
        assertEquals(task, retrieved)  // Whole object assertion
    }
}
```

**Integration Tests** (A2AClientIntegrationTest pattern):
```kotlin
@Testcontainers
class A2AClientIntegrationTest {
    @Container
    val testA2AServer = GenericContainer("test-python-a2a-server")
        .withExposedPorts(9999)
        .waitingFor(Wait.forListeningPort())

    @Test
    fun `test get agent card`() = runTest {
        val agentCard = client.getAgentCard()
        val expectedAgentCard = AgentCard(...)
        assertEquals(expectedAgentCard, agentCard)  // Full object comparison
    }
}
```

## Implementation Patterns

### Transport Layer Abstraction
- Implement `ClientTransport` interface for new client protocols
- Implement `ServerTransport` and `RequestHandler` for new server protocols
- Keep protocol logic separate from transport mechanism
- Use suspend functions for all transport operations

### Storage Interface Pattern
```kotlin
public interface SomeStorage {
    public suspend fun save(item: Item)
    public suspend fun get(id: String): Item?
    public suspend fun delete(id: String)
}

// In-memory implementation for testing/development
@OptIn(InternalA2AApi::class)
public class InMemorySomeStorage : SomeStorage {
    private val rwLock = RWLock()
    private val items = mutableMapOf<String, Item>()

    override suspend fun save(item: Item) = rwLock.withWriteLock {
        items[item.id] = item
    }
}
```

### Session Management Pattern
- Use `SessionEventProcessor` for event-driven session handling
- Implement proper cleanup with `Closeable` interface
- Use structured concurrency with `CoroutineScope`
- Handle session lifecycle properly (start → active → closed)

### Agent Executor Implementation
```kotlin
public class MyAgentExecutor : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        // Process request and send events through eventProcessor
        eventProcessor.sendTaskEvent(/* task event */)
    }
}
```

## Some Common Commands

```bash
# Build specific modules (a2a is a meta-module, build individual modules)
../gradlew :a2a:a2a-core:assemble
../gradlew :a2a:a2a-client:assemble
../gradlew :a2a:a2a-server:assemble

# Run JVM tests for specific modules
../gradlew :a2a:a2a-core:jvmTest
../gradlew :a2a:a2a-client:jvmTest
../gradlew :a2a:a2a-server:jvmTest
../gradlew :a2a:a2a-transport:a2a-transport-core-jsonrpc:jvmTest

# Run JS tests for specific modules
../gradlew :a2a:a2a-core:jsTest
../gradlew :a2a:a2a-client:jsTest
../gradlew :a2a:a2a-server:jsTest

# Build all non-transport a2a modules at once
../gradlew :a2a:a2a-core:assemble :a2a:a2a-client:assemble :a2a:a2a-server:assemble

# Run all JVM tests across a2a modules
../gradlew :a2a:a2a-core:jvmTest :a2a:a2a-client:jvmTest :a2a:a2a-server:jvmTest
```
