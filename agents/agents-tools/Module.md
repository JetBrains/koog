# Module agents-tools

A module that provides a framework for defining, describing, and executing tools that can be used by AI agents to interact with the environment.

### Overview

The agents-tools module provides a comprehensive framework for creating and managing tools that AI agents can use to perform actions. It includes:

- A core `Tool` abstract class that represents a tool with arguments and results
- Descriptors for tools and their parameters, allowing for rich metadata
- Reflection-based utilities for converting Kotlin functions to tools
- Support for serialization and deserialization of tool arguments and results
- A `ToolSet` interface for grouping related tools together

Tools are designed to be executed within an environment context, ensuring proper handling of events, feature pipelines, and testing capabilities.

### Using in your project

To use tools in your project:

1. Create a class that implements the `ToolSet` interface
2. Annotate methods with `@Tool` and `@LLMDescription` to define tools
3. Use the `asTools()` extension function to convert the methods to a list of tools
4. Register the tools with your agent or environment

```kotlin
class MyToolSet : ToolSet {
    @Tool
    @LLMDescription("Performs a calculation")
    fun calculate(
        @LLMDescription("First number") a: Int,
        @LLMDescription("Second number") b: Int
    ): Int {
        return a + b
    }
}

// Convert to tools
val myToolSet = MyToolSet()
val tools = myToolSet.asTools()
```

### Using in unit tests

For unit testing tools:

1. Create instances of your tool classes or tool sets
2. Use the `asTools()` or `asTool()` functions to convert them to tools
3. Execute the tools directly with test arguments
4. Verify the results

```kotlin
@Test
fun testCalculateTool() {
    val myToolSet = MyToolSet()
    val tools = myToolSet.asTools()
    val calculateTool = tools.first { it.name == "calculate" }

    // Create a DirectToolCallsEnabler for testing
    val enabler = object : DirectToolCallsEnabler {}

    // Execute the tool
    val args = calculateTool.decodeArgsFromString("""{"a": 5, "b": 3}""")
    val result = runBlocking { calculateTool.execute(args, enabler) }

    assertEquals(8, result.result)
}
```

### Example of usage

Here's a complete example of defining and using tools:

```kotlin
// Define a tool set
class MathTools : ToolSet {
    @Tool
    @LLMDescription("Adds two numbers")
    fun add(
        @LLMDescription("First number") a: Int,
        @LLMDescription("Second number") b: Int
    ): Int = a + b

    @Tool
    @LLMDescription("Multiplies two numbers")
    fun multiply(
        @LLMDescription("First number") a: Int,
        @LLMDescription("Second number") b: Int
    ): Int = a * b
}

// Use the tools
fun main() {
    val mathTools = MathTools()
    val tools = mathTools.asTools()

    // Create a tool registry
    val registry = ToolRegistry.Builder().apply {
        tools
    }.build()

    // Find and use a tool (in a real scenario, this would be done through an agent)
    val enabler = object : DirectToolCallsEnabler {}
    val addTool = registry.findTool("add")
    val args = addTool.decodeArgsFromString("""{"a": 10, "b": 20}""")

    runBlocking {
        val (result, stringResult) = addTool.executeAndSerialize(args, enabler)
        println("Result: $stringResult") // Output: Result: 30
    }
}
```
