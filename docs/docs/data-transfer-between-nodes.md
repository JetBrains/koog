## Overview

Koog provides a way to store and pass data using `AIAgentStorage`, which is a key-value storage system designed as a
type-safe way to pass data between different nodes or even subgraphs.

The storage is accessible through the `storage` property (`storage: AIAgentStorage`) available in agent nodes, allowing
for seamless data sharing across different components of your AI agent system.

## Key and value structure

The key-value data storage structure relies on the `AIAgentStorageKey` data class. For more information about
`AIAgentStorageKey`, see the sections below.

### AIAgentStorageKey

The storage uses a typed key system to provide type safety when storing and retrieving data.

`AIAgentStorageKey<T>` class represents a storage key used for identifying and accessing data. Here are
  the key features of this class:

- The generic type parameter `T` specifies the type of data associated with this key, providing type safety.

- Each key has a `name` property which is a string identifier for easier identification and debugging.

- Each key instance is unique. `name` is not used to determine uniqueness, so it is acceptable to have several 
  keys with the same name. This allows resuing existing strategy components without the risk of 
  accidentally overwriting your data in the storage.

## Usage examples

The following sections provide an actual example of creating a storage key and using it to store and retrieve data.

### Defining a class that represents your data

The first step in storing data that you want to pass is creating a class that represents your data. Here is an example
of a simple class with basic user data:

=== "Kotlin"

    ```kotlin
    class UserData(
       val name: String,
       val age: Int
    )
    ```
    <!--- KNIT example-data-transfer-between-nodes-01.kt -->

=== "Java"

    ```java
    record UserData(
        String name,
        int age
    ) {}
    ```
    <!--- KNIT exampleDataTransferBetweenNodesJava01.java -->


Once defined, use the class to create a storage key as described below.

### Creating a storage key

Create a typed storage key for the defined data structure:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.createStorageKey
    class UserData(
        val name: String,
        val age: Int
    )
    -->
    ```kotlin
    val userDataKey = createStorageKey<UserData>("user-data")
    ```
    <!--- KNIT example-data-transfer-between-nodes-02.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentStorage;
    import ai.koog.agents.core.agent.entity.AIAgentStorageKey;
    import ai.koog.serialization.TypeToken;
    class exampleDataTransferBetweenNodesJava02 {
        record UserData(
            String name,
            int age
        ) {}
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    AIAgentStorageKey<UserData> userDataKey = AIAgentStorage.createStorageKey("user-data", TypeToken.of(UserData.class));
    ```
    <!--- KNIT exampleDataTransferBetweenNodesJava02.java -->

The `createStorageKey` function takes a string parameter used for identification and debugging purposes, and a `TypeToken` representing the value type (in Java; Kotlin uses reified generics automatically).

### Storing data

To save data using a created storage key, use the `storage.set(key: AIAgentStorageKey<T>, value: T)` method in a node:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.agent.entity.createStorageKey
    class UserData(
       val name: String,
       val age: Int
    )
    val userDataKey = createStorageKey<UserData>("user-data")
    -->
    ```kotlin
    val nodeSaveData by node<Unit, Unit> {
        storage.set(userDataKey, UserData("John", 26))
    }
    ```
    <!--- KNIT example-data-transfer-between-nodes-03.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.agents.core.agent.entity.AIAgentStorage;
    import ai.koog.agents.core.agent.entity.AIAgentStorageKey;
    import ai.koog.serialization.TypeToken;
    public class exampleDataTransferBetweenNodesJava03 {
        record UserData(
            String name,
            int age
        ) {}
        public static void main(String[] args) {
            AIAgentStorageKey<UserData> userDataKey = AIAgentStorage.createStorageKey("user-data", TypeToken.of(UserData.class));
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var nodeSaveData = AIAgentNode.builder("nodeSaveData")
        .withInput(String.class)
        .withOutput(String.class)
        .withAction((input, ctx) -> {
            ctx.getStorage().set(userDataKey, new UserData("John", 26));
            return "";
        })
        .build();
    ```
    <!--- KNIT exampleDataTransferBetweenNodesJava03.java -->

### Retrieving data

To retrieve the data, use the `storage.get` method in a node:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.createStorageKey
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    class UserData(
        val name: String,
        val age: Int
    )
    fun main() {
        val userDataKey = createStorageKey<UserData>("user-data")
        val str = strategy<String, Unit>("my-strategy") {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    val nodeRetrieveData by node<String, Unit> { message ->
        storage.get(userDataKey)?.let { userFromStorage ->
            println("Hello dear $userFromStorage, here's a message for you: $message")
        }
    }
    ```
    <!--- KNIT example-data-transfer-between-nodes-04.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.agents.core.agent.entity.AIAgentStorage;
    import ai.koog.agents.core.agent.entity.AIAgentStorageKey;
    import ai.koog.serialization.TypeToken;
    public class exampleDataTransferBetweenNodesJava04 {
        record UserData(
            String name,
            int age
        ) {}
        public static void main(String[] args) {
            AIAgentStorageKey<UserData> userDataKey = AIAgentStorage.createStorageKey("user-data", TypeToken.of(UserData.class));
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var nodeRetrieveData = AIAgentNode.builder("nodeRetrieveData")
        .withInput(String.class)
        .withOutput(String.class)
        .withAction((message, ctx) -> {
            var userData = ctx.getStorage().get(userDataKey);
            System.out.println("Hello dear %s, here's a message for you: %s".formatted(userData, message));
            return "";
        })
        .build();
    ```
    <!--- KNIT exampleDataTransferBetweenNodesJava04.java -->

## API documentation

For a complete reference related to the `AIAgentStorage` class, see [AIAgentStorage](api:agents-core::ai.koog.agents.core.agent.entity.AIAgentStorage).

For individual functions available in the `AIAgentStorage` class, see the following API references:

- [clear](api:agents-core::ai.koog.agents.core.agent.entity.AIAgentStorage.clear)
- [get](api:agents-core::ai.koog.agents.core.agent.entity.AIAgentStorage.get)
- [getValue](api:agents-core::ai.koog.agents.core.agent.entity.AIAgentStorage.getValue)
- [putAll](api:agents-core::ai.koog.agents.core.agent.entity.AIAgentStorage.putAll)
- [remove](api:agents-core::ai.koog.agents.core.agent.entity.AIAgentStorage.remove)
- [set](api:agents-core::ai.koog.agents.core.agent.entity.AIAgentStorage.set)
- [toMap](api:agents-core::ai.koog.agents.core.agent.entity.AIAgentStorage.toMap)

## Additional information

- `AIAgentStorage` is thread-safe, using a Mutex to ensure concurrent access is handled properly.
- When retrieving values, type casting is handled automatically, ensuring type safety throughout your application.
- For non-nullable access to values, use the `getValue` method which throws an exception if the key does not exist.
- You can clear the storage entirely using the `clear` method, which removes all stored key-value pairs.
