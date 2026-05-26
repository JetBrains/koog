# Pitfalls, testing API, and docs classpath

Load this file when something fails to compile, or when you're writing a `MockPromptExecutor`-based testing block.

## Common pitfalls

### `var` is only for local variables

Java `var` (type inference) works only for local variables inside methods. For fields, use explicit types:

```java
// WRONG: static var logger = LoggerFactory.getLogger("x");
// RIGHT: static Logger logger = LoggerFactory.getLogger("x");
```

### Inline value classes make constructors private from Java

Kotlin classes with `Duration`, `Duration?`, or other inline value class parameters in their constructors generate synthetic constructors with `DefaultConstructorMarker`. These are not callable from Java. Solution: add a JVM `create()` factory — see `factory-classes.md`.

### Import management

Always add imports to the INCLUDE block. Never use fully-qualified names in visible code:

```java
// WRONG (in visible code): private static final org.slf4j.Logger logger = ...
// RIGHT (in INCLUDE):      import org.slf4j.Logger;
// RIGHT (in visible code): static Logger logger = ...
```

### `@JvmOverloads` check

Before calling a Kotlin constructor/method with default parameters from Java, verify it has `@JvmOverloads`. Without it, Java only sees the full-parameter version (or synthetic constructors with `DefaultConstructorMarker`).

### SUFFIX brace counting

Count opening braces in INCLUDE carefully. The SUFFIX must have exactly the matching number of closing braces. Off-by-one errors cause `Syntax error: Expecting a top level declaration` at the end of the generated file. After writing SUFFIX, mentally concatenate INCLUDE + visible code + SUFFIX and verify all braces match.

### `is`-prefixed Kotlin properties

Kotlin `val isOpen: StateFlow<Boolean>` generates Java getter `isOpen()` (not `getIsOpen()`). The `is` prefix follows JavaBeans convention.

### Kotlin `expect`/`actual` classes

When an `expect abstract class` in `commonMain` has `abstract` members, Java subclasses must implement them. Check the **JVM actual** (in `jvmCommonMain`), not the `expect` declaration — the actual may provide default implementations or bridge methods not visible in the `expect`.

### Legacy `/** **/` placeholder pattern

Some docs have existing Java tabs that wrap code in `/** */` block comments (making the code a Javadoc comment that trivially compiles). This is a legacy pattern — **always replace with real compilable Java code** or explanatory comments for Kotlin-only APIs.

## Testing API: `MockPromptExecutor.builder`

The testing module (`agents-test`) provides Java-friendly builder APIs via `MockPromptExecutor.builder(serializer)`.

### `MockExecutorBuilder` methods

| Method | Description |
|--------|-------------|
| `mockLLMAnswer(text)` | Returns `MockLLMAnswerBuilder` — configure with `.asDefaultResponse()`, `.onRequestContains(pattern)`, `.onRequestEquals(pattern)`, `.onCondition(pred)`. |
| `mockLLMToolCall(tool, args)` | Returns `MockLLMToolCallBuilder` — configure with `.onRequestEquals(pattern)`, `.onRequestContains(pattern)`, `.onCondition(pred)`. |
| `mockTool(tool)` | Returns `MockToolBuilder` — configure with `.alwaysReturns(result)`, `.alwaysDoes(action)`, `.returns(result).onArguments(args)`, `.returns(result).onArgumentsMatching(pred)`. |
| `clock(clock)` | Set custom clock. |
| `tokenizer(tokenizer)` | Set custom tokenizer. |
| `handleLastAssistantMessage(bool)` | Configure last-assistant-message handling. |
| `build()` | Returns `PromptExecutor`. |

All sub-builders return `MockExecutorBuilder` for method chaining.

### Full Java test pattern

```java
PromptExecutor mockLLMApi = MockPromptExecutor.builder(new JacksonSerializer())
    .mockLLMToolCall(SayToUser.INSTANCE, new SayToUser.Args(positiveText))
        .onRequestEquals(positiveText)
    .mockLLMAnswer(positiveResponse).onRequestContains(positiveResponse)
    .mockLLMAnswer(defaultText).asDefaultResponse()
    .mockTool(SayToUser.INSTANCE).alwaysReturns(positiveResponse)
    .build();

var agent = AIAgent.builder()
    .promptExecutor(mockLLMApi)
    .llmModel(OpenAIModels.Chat.GPT4o)
    .install(Testing.Feature, config -> { })
    .build();

String result = agent.run(positiveText);
```

### Importing Kotlin objects in Java INCLUDE

When Java code needs Kotlin objects defined in a Kotlin example's generated package, import from the generated package:

```java
import ai.koog.agents.example.exampleTesting03.*;
```

This gives access to tool definitions and other objects declared at the top level of the Kotlin example file.

## Docs compilation environment

### JDK version

The project requires JDK 17. If the default JDK is newer (e.g., 25), compilation fails with cryptic errors. Use:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew :docs:knit
JAVA_HOME=/path/to/jdk-17 ./gradlew :docs:compileJava
```

### Docs classpath constraints

The docs module has a limited classpath:

- **JUnit is NOT available.** Do not use `import org.junit.jupiter.api.Assertions.*`. Use Java `assert` statements instead: `assert expected.equals(result) : "message";`
- **JacksonSerializer IS available:** `import ai.koog.serialization.jackson.JacksonSerializer;`
- **agents-core, agents-test, agents-ext ARE available:** agent builders, mock executors, `SayToUser` tool, etc.

If compilation fails with "package X does not exist", check `docs/build.gradle.kts` for available dependencies.
