# Serialization

## Introduction

Koog uses a thin, library-agnostic serialization layer to convert tool arguments and results to and from JSON.
This layer sits between the agent runtime and the underlying serialization library, so you can swap the library
without changing any tool or agent code.

By default, Koog uses `KotlinxSerializer` (backed by kotlinx-serialization).
On the JVM you can also switch to `JacksonSerializer` (backed by jackson-databind).

## The `JSONSerializer` interface

`JSONSerializer` is the core abstraction that lives in `serialization-core`.
The interface has four primary methods (encode/decode to both strings and `JSONElement`),
plus two convenience methods for converting between `JSONElement` and strings:

- `encodeToString` / `decodeFromString` — serialize a typed value to/from a JSON string.
- `encodeToJSONElement` / `decodeFromJSONElement` — serialize a typed value to/from a `JSONElement` tree.
- `encodeJSONElementToString` / `decodeJSONElementFromString` — convert between `JSONElement` and its string form.

The following example shows all key operations:

<!--- INCLUDE
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable

-->

```kotlin
@Serializable
data class User(val name: String, val age: Int)

val serializer: JSONSerializer = KotlinxSerializer()

// Encode a data class to a JSON string
val json: String = serializer.encodeToString(User("Alice", 30), typeToken<User>())

// Decode a JSON string back to a data class
val user: User = serializer.decodeFromString(json, typeToken<User>())

// Encode to a JSONElement tree
val element: JSONElement = serializer.encodeToJSONElement(user, typeToken<User>())

// Decode from a JSONElement tree
val userFromElement: User = serializer.decodeFromJSONElement(element, typeToken<User>())

// Convert between JSONElement and a raw JSON string
val jsonString = """{"key": "value"}"""
val jsonElement: JSONElement = serializer.decodeJSONElementFromString(jsonString)
val backToString: String = serializer.encodeJSONElementToString(jsonElement)
```

<!--- KNIT example-tool-serialization-01.kt -->

## Type tokens

`TypeToken` is how Koog passes type information at runtime.

### Kotlin

<!--- INCLUDE
import ai.koog.serialization.typeToken

-->

```kotlin
data class MyClass(val value: String)

fun typeTokenExamples() {
    // Inline reified — preferred in Kotlin
    val tokenReified = typeToken<MyClass>()

    // From a KClass (when no reified type parameter is available)
    val tokenKClass = typeToken(MyClass::class)

    // Generic type — preserves type arguments at runtime
    val tokenGeneric = typeToken<List<String>>()
}
```

<!--- KNIT example-tool-serialization-02.kt -->

### Java

```java
// Simple class
TypeToken token = TypeToken.of(MyClass.class);

// Generic type — use TypeCapture to preserve type arguments
TypeToken token = TypeToken.of(new TypeCapture<List<String>>() {});
```

## `JSONElement` — library-agnostic JSON tree

`JSONElement` is a neutral intermediate representation for JSON data.
It exists so that serializers, tools, and agent internals do not depend on specific JSON types from a particular
library.

### Hierarchy

```
JSONElement
├── JSONObject   – key-value pairs  (entries: Map<String, JSONElement>)
├── JSONArray    – ordered list      (elements: List<JSONElement>)
└── JSONPrimitive
    ├── JSONLiteral  – string, number, or boolean
    └── JSONNull     – JSON null singleton
```

### Conversion to and from library types

Each serialization integration provides extension functions that let you convert between `JSONElement` and the
library's own dynamic JSON type. This is useful when you already have a `JsonElement` or `JsonNode` and
want to pass it to a Koog (or vice versa), without going through a full encode/decode cycle.

### Building and reading elements

<!--- INCLUDE
import ai.koog.serialization.JSONArray
import ai.koog.serialization.JSONLiteral
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
-->

```kotlin
val obj = JSONObject(
    mapOf(
        "name" to JSONPrimitive("Alice"),
        "age" to JSONPrimitive(30),
        "active" to JSONPrimitive(true),
    )
)

val arr = JSONArray(listOf(JSONPrimitive(1), JSONPrimitive(2), JSONPrimitive(3)))

// Reading values from an object
val nameContent: String = (obj.entries["name"] as JSONPrimitive).content  // "Alice"
val age: Int? = (obj.entries["age"] as JSONPrimitive).intOrNull // 30
```

<!--- KNIT example-tool-serialization-03.kt -->

## Supported serializers

### `KotlinxSerializer` (default)

- **Module**: `ai.koog:serialization-core` (included transitively with `ai.koog:agents-core`)
- **Backed by**: kotlinx-serialization
- **JSONElement mappers**: `JsonElement.toKoogJSONElement()` / `JSONElement.toKotlinxJsonElement()` (and per-subtype variants)

<!--- INCLUDE
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.serialization.json.Json
-->

```kotlin
// Default instance — uses Json.Default
val defaultSerializer = KotlinxSerializer()

// Custom Json configuration
val customSerializer = KotlinxSerializer(
    json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
)
```

<!--- KNIT example-tool-serialization-04.kt -->

### `JacksonSerializer` (JVM only)

- **Module**: `ai.koog:serialization-jackson` (separate dependency)
- **Backed by**: jackson-databind
- **JSONElement mappers**: `JsonNode.toKoogJSONElement()` / `JSONElement.toJacksonJsonNode()` (and per-subtype variants)

Add the dependency to your `build.gradle.kts`:

```kts
dependencies {
    implementation("ai.koog:serialization-jackson:<version>")
}
```

Then create the serializer:

<!--- INCLUDE
import ai.koog.serialization.jackson.JacksonSerializer
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
-->

```kotlin
// Default instance — uses a fresh ObjectMapper with JSONElementModule pre-registered
val defaultSerializer = JacksonSerializer()

// Custom ObjectMapper configuration
val customSerializer = JacksonSerializer(
    objectMapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
)
```

<!--- KNIT example-tool-serialization-05.kt -->

!!! note
    `JacksonSerializer` automatically registers `JSONElementModule` on the `ObjectMapper` it uses for
    proper serialization/deserialization of the `JSONElement` types.

## Configuring the serializer in `AIAgentConfig`

Pass the `serializer` parameter when constructing `AIAgentConfig`.
If you omit it, `KotlinxSerializer()` is used.

<!--- INCLUDE
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.serialization.jackson.JacksonSerializer
-->

```kotlin
val agentConfig = AIAgentConfig(
    prompt = prompt("assistant") {
        system("You are a helpful assistant.")
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 10,
    serializer = JacksonSerializer()
)
```

<!--- KNIT example-tool-serialization-06.kt -->

## How tools interact with the serializer

The agent runtime calls the following methods on each `Tool` instance automatically.
You do not need to invoke them yourself in normal usage.

- **`decodeArgs(rawArgs, serializer)`** (JSON → TArgs) — deserializes raw JSON arguments from the LLM into the tool's typed args class.
- **`encodeArgs(args, serializer)`** (TArgs → JSON) — serializes typed args back to JSON (used by certain agent features).
- **`decodeResult(rawResult, serializer)`** (JSON → TResult) — deserializes a stored JSON result.
- **`encodeResult(result, serializer)`** (TResult → JSON) — serializes the tool's result to JSON.
- **`encodeResultToString(result, serializer)`** (TResult → String) — serializes the tool's result to a string sent to the LLM.
  By default, delegates to `encodeResult`. Can be overridden to customize the result format for the LLM.

These methods are `open` on `Tool`, so you can override them if you need custom serialization behavior for a
specific tool.
