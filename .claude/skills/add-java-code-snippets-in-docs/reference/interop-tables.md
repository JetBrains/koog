# Kotlin ↔ Java Interop Reference

Load this file when writing the Java side of a non-trivial Kotlin block: figuring out how a Kotlin construct maps to Java, or whether a `suspend` function has a Java-friendly bridge.

## Quick mapping table

| Kotlin | Java |
|--------|------|
| `AIAgent(...) { }` | `AIAgent.builder()...build()` |
| `install(Feature) { }` | `.install(Feature.Feature, config -> { })` |
| `install(Testing) { config() }` | `.install(Testing.Feature, config -> { })` (`ConfigureAction<T>` is a `fun interface`) |
| `simpleOllamaAIExecutor()` | `PromptExecutor.builder().ollama().build()` |
| `KotlinLogging.logger { }` | `LoggerFactory.getLogger("name")` |
| `val x = ...` (local) | `var x = ...` |
| `val x = ...` (field) | `static Type x = ...` (never `static var`) |
| `runBlocking { }` | not needed (`agent.run` is blocking in Java — see suspend bridges below) |
| `config.addMessageProcessor(...)` | `config.addMessageProcessor(...)` |
| `class Foo : Bar()` | `class Foo extends Bar` |
| `when (x) { is Type -> ... }` | `if (x instanceof Type) { ... } else if ...` |
| `override suspend fun processMessage(msg)` | `void handleMessage(FeatureMessage msg)` — see suspend bridges |
| `override suspend fun close()` | `void handleClose()` — see suspend bridges |
| `TraceFeatureMessageFileWriter(path, sinkOpener)` | `TraceFeatureMessageFileWriter.create(path)` — JVM `create()` overload taking `java.nio.file.Path` |
| `TraceFeatureMessageLogWriter(klogger)` | `TraceFeatureMessageLogWriter.create(slf4jLogger)` — JVM `create()` overload taking SLF4J `Logger` |
| `TraceFeatureMessageRemoteWriter(config)` | `new TraceFeatureMessageRemoteWriter()` (direct, no factory needed) |
| `getMockExecutor(...) { mockLLMAnswer(...) }` | `MockPromptExecutor.builder(serializer).mockLLMAnswer(...).build()` |

## Suspend functions and Java

Kotlin `suspend` functions compile to JVM methods with an extra `Continuation` parameter and `Object` return type. **Java code snippets must NEVER show `Continuation` parameters.** The codebase provides Java-friendly bridges.

### Calling suspend methods from Java

Methods annotated with `@JavaAPI` and `@JvmName` provide blocking wrappers. Use the Java-facing name directly:

| Kotlin (suspend) | Java (blocking) |
|------------------|-----------------|
| `agent.run(input)` | `agent.run(input)` (calls `javaNonSuspendRun` via `@JvmName("run")`) |
| `processor.initialize()` | `processor.initialize()` (calls `javaNonSuspendInitialize` via `@JvmName`) |
| `processor.onMessage(msg)` | `processor.onMessage(msg)` (calls `javaNonSuspendOnMessage` via `@JvmName`) |

These wrappers use `runBlockingIfRequired()` internally. Java code calls them like normal blocking methods.

### Overriding suspend methods from Java

When a Java class extends a Kotlin class with `suspend` methods to override, look for non-suspend bridge methods annotated with `@JavaAPI`:

| Kotlin override | Java override |
|-----------------|---------------|
| `override suspend fun processMessage(msg)` | `void handleMessage(FeatureMessage msg)` |
| `override suspend fun close()` | `void handleClose()` |

**Why the names differ:** `@JvmName` cannot be used on `open` or `override` methods (Kotlin restriction — it would break polymorphism). So Java-facing override names can diverge from the Kotlin suspend names (`handleMessage` not `processMessage`).

### Abstract properties with Kotlin types

Abstract properties like `val isOpen: StateFlow<Boolean>` leak Kotlin coroutine types to Java. If the base class provides a default `open val` implementation (e.g., via `MutableStateFlow`), Java subclasses don't need to implement it. Check whether the property is `abstract` or `open` before writing the Java example.

### Before writing a Java subclass example

1. Check what `@JavaAPI` bridge methods exist on the class — grep for `@JavaAPI` in the `jvmCommonMain` actual.
2. Check which members are `abstract` vs `open` — abstract ones MUST be implemented in Java.
3. Never show `Continuation`, `Object` return types, or `Unit.INSTANCE` in Java — use the bridge methods.

## `is`-prefixed Kotlin properties

Kotlin `val isOpen: StateFlow<Boolean>` generates Java getter `isOpen()` (not `getIsOpen()`). The `is` prefix follows JavaBeans convention for boolean-like property names.

## Key source files to grep

When writing Java examples that use these classes, check the JVM actuals for available `@JavaAPI` bridge methods:

- `agents/agents-core/src/jvmCommonMain/kotlin/ai/koog/agents/core/agent/AIAgent.kt` — `javaNonSuspendRun` with `@JvmName("run")`.
- `agents/agents-core/src/jvmCommonMain/kotlin/ai/koog/agents/core/feature/message/FeatureMessageProcessor.kt` — calling bridges (`javaNonSuspendInitialize`, `javaNonSuspendOnMessage`) and overriding bridges (`handleMessage`, `handleClose`).
- `agents/agents-test/src/commonMain/kotlin/ai/koog/agents/testing/tools/MockExecutorBuilder.kt` — Java builder for mock executor: `mockLLMAnswer()`, `mockLLMToolCall()`, `mockTool()` + inner builder classes.
- `agents/agents-test/src/commonMain/kotlin/ai/koog/agents/testing/tools/MockPromptExecutor.kt` — entry point: `MockPromptExecutor.builder(serializer)` with `@JvmStatic @JavaAPI`.
- `agents/agents-test/src/commonMain/kotlin/ai/koog/agents/testing/tools/MockExecutorDSLBuilder.kt` — Kotlin DSL builder (reference for understanding what the Java builder delegates to).
- `agents/agents-test/src/commonMain/kotlin/ai/koog/agents/testing/feature/TestingFeature.kt` — `Testing.Feature` companion object (used in Java as `Testing.Feature`).
- `agents/agents-core/src/jvmTest/java/ai/koog/agents/core/agent/JavaAPIAgentBuilderJavaTest.java` — reference Java test using `MockPromptExecutor`, `AIAgent.builder()`, and functional strategies.
