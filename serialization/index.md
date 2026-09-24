# Serialization

## Introduction

Koog uses a thin, library-agnostic serialization layer to convert tool arguments and results to and from JSON. This layer sits between the agent runtime and the underlying serialization library, so you can swap the library without changing any tool or agent code.

Beyond tools, the serialization layer is also used by agent features such as **Persistence** to serialize and deserialize node inputs and outputs.

By default, Koog uses `KotlinxSerializer` (backed by kotlinx-serialization). On the JVM you can also switch to `JacksonSerializer` (backed by jackson-databind).

## The `JSONSerializer` interface

`JSONSerializer` is the core abstraction that lives in `serialization-core`. The interface has four primary methods (encode/decode to both strings and `JSONElement`), plus two convenience methods for converting between `JSONElement` and strings:

- `encodeToString` / `decodeFromString` — serialize a typed value to/from a JSON string.
- `encodeToJSONElement` / `decodeFromJSONElement` — serialize a typed value to/from a `JSONElement` tree.
- `encodeJSONElementToString` / `decodeJSONElementFromString` — convert between `JSONElement` and its string form.

The following example shows all key operations:

```
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

```
// Jackson-serializable class
record User(
    @JsonProperty("name") String name,
    @JsonProperty("age") int age
) {}

var serializer = new JacksonSerializer();

// Encode a data class to a JSON string
String json = serializer.encodeToString(new User("Alice", 30), TypeToken.of(User.class));

// Decode a JSON string back to a data class
User user = serializer.decodeFromString(json, TypeToken.of(User.class));

// Encode to a JSONElement tree
JSONElement element = serializer.encodeToJSONElement(user, TypeToken.of(User.class));

// Decode from a JSONElement tree
User userFromElement = serializer.decodeFromJSONElement(element, TypeToken.of(User.class));

// Convert between JSONElement and a raw JSON string
String jsonString = "{\"key\": \"value\"}";
JSONElement jsonElement = serializer.decodeJSONElementFromString(jsonString);
String backToString = serializer.encodeJSONElementToString(jsonElement);
```

## Type tokens

`TypeToken` is how Koog passes type information at runtime.

```
data class MyClass(val value: String)

// Inline reified — preferred in Kotlin
val tokenReified = typeToken<MyClass>()

// From a KClass (when no reified type parameter is available)
val tokenKClass = typeToken(MyClass::class)

// Generic type — preserves type arguments at runtime
val tokenGeneric = typeToken<List<String>>()
```

```
record MyClass(
    String value
) {}

// Simple class
TypeToken tokenClass = TypeToken.of(MyClass.class);

// Generic type — use TypeCapture to preserve type arguments
TypeToken tokenGeneric = TypeToken.of(new TypeCapture<List<String>>() {});
```

## `JSONElement` — library-agnostic JSON tree

`JSONElement` is a neutral intermediate representation for JSON data. It exists so that serializers, tools, and agent internals do not depend on specific JSON types from a particular library.

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

Each serialization integration provides extension functions that let you convert between `JSONElement` and the library's own dynamic JSON type. This is useful when you already have a `JsonElement`, `JsonNode`, etc. and want to pass it to Koog (or vice versa), without going through a full encode/decode cycle. Examples are provided below for each supported library.

### Building and reading elements

```
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

```
JSONObject obj = new JSONObject(
    Map.of(
        "name", JSONPrimitive.of("Alice"),
        "age", JSONPrimitive.of(30),
        "active", JSONPrimitive.of(true)
    )
);

JSONArray arr = new JSONArray(List.of(JSONPrimitive.of(1), JSONPrimitive.of(2), JSONPrimitive.of(3)));

// Reading values from an object
String nameContent = ((JSONPrimitive) obj.getEntries().get("name")).getContent();  // "Alice"
Integer age = ((JSONPrimitive) obj.getEntries().get("age")).getIntOrNull(); // 30
```

## Supported serializers

### `KotlinxSerializer` (default)

- **Module**: `ai.koog:serialization-core` (included transitively with `ai.koog:agents-core`)
- **Backed by**: kotlinx-serialization

```
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

You can also convert between Koog's `JSONElement` and kotlinx-serialization's `JsonElement`

```
val koogJson: JSONElement = JSONObject(
    mapOf(
        "key" to JSONPrimitive("value")
    )
)

// Convert to kotlinx-serialization dynamic JSON instance
val kotlinxJson: JsonElement = koogJson.toKotlinxJsonElement()

// Convert to Koog dynamic JSON instance
val koogJsonConverted: JSONElement = kotlinxJson.toKoogJSONElement()
```

### `JacksonSerializer` (JVM only)

- **Module**: `ai.koog:serialization-jackson` (separate dependency)
- **Backed by**: jackson-databind

Add the dependency to your `build.gradle.kts`:

```
dependencies {
    implementation("ai.koog:serialization-jackson:<version>")
}
```

Then create the serializer:

```
// Default instance — uses a fresh ObjectMapper with JSONElementModule pre-registered
val defaultSerializer = JacksonSerializer()

// Custom ObjectMapper configuration
val customSerializer = JacksonSerializer(
    objectMapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
)
```

```
// Default instance — uses a fresh ObjectMapper with JSONElementModule pre-registered
var defaultSerializer = new JacksonSerializer();

// Custom ObjectMapper configuration
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
var customSerializer = new JacksonSerializer(objectMapper);
```

Note

`JacksonSerializer` automatically registers `JSONElementModule` on the `ObjectMapper` it uses for proper serialization/deserialization of the `JSONElement` types.

You can also convert between Koog's `JSONElement` and Jackson's `JsonNode`

```
val koogJson: JSONElement = JSONObject(
    mapOf(
        "key" to JSONPrimitive("value")
    )
)

// Convert to Jackson dynamic JSON instance
val jacksonJson: JsonNode = koogJson.toJacksonJsonNode()

// Convert to Koog dynamic JSON instance
val koogJsonConverted: JSONElement = jacksonJson.toKoogJSONElement()
```

```
JSONElement koogJson = new JSONObject(
    Map.of(
        "key", JSONPrimitive.of("value")
    )
);

// Convert to Jackson dynamic JSON instance
JsonNode jacksonJson = JacksonJSONElementMappers.toJacksonJsonNode(koogJson);

// Convert to Koog dynamic JSON instance
JSONElement koogJsonConverted = JacksonJSONElementMappers.toKoogJSONElement(jacksonJson);
```

## Configuring the serializer in `AIAgentConfig`

Pass the `serializer` parameter when constructing `AIAgentConfig`. If you omit it, `KotlinxSerializer` is used.

```
val agentConfig = AIAgentConfig(
    prompt = prompt("assistant") {
        system("You are a helpful assistant.")
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 10,
    serializer = JacksonSerializer()
)
```

Pass the `serializer` parameter when constructing `AIAgentConfig`. If you omit it, `JacksonSerializer` is used.

```
var agentConfig = AIAgentConfig.builder()
    .model(OpenAIModels.Chat.GPT4o)
    .prompt(
        Prompt.builder("assistant")
            .system("You are a helpful assistant")
            .build()
    )
    .maxAgentIterations(10)
    .serializer(new JacksonSerializer())
    .build();
```

## How tools interact with the serializer

The agent runtime calls the following methods on each `Tool` instance automatically. You do not need to invoke them yourself in normal usage.

- **`decodeArgs(rawArgs, serializer)`** (JSON → TArgs) — deserializes raw JSON arguments from the LLM into the tool's typed args class.
- **`encodeArgs(args, serializer)`** (TArgs → JSON) — serializes typed args back to JSON (used by certain agent features).
- **`decodeResult(rawResult, serializer)`** (JSON → TResult) — deserializes a stored JSON result.
- **`encodeResult(result, serializer)`** (TResult → JSON) — serializes the tool's result to JSON.
- **`encodeResultToString(result, serializer)`** (TResult → String) — serializes the tool's result to a string sent to the LLM. By default, delegates to `encodeResult`. Can be overridden to customize the result format for the LLM.

These methods are `open` on `Tool`, so you can override them if you need custom serialization behavior for a specific tool.

## How features use the serializer

The serialization layer is not limited to tools — certain agent features rely on it as well.

For example, **Persistence** uses `JSONSerializer` configured in `AIAgentConfig` to serialize and deserialize node inputs and outputs when creating checkpoints and restoring agent state. This means any type that flows through a persisted node must be serializable by the configured `JSONSerializer`.

See [Agent Persistence](../features/agent-persistence/) for details on checkpoint creation and restoration.
