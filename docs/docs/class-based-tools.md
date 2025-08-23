# Class-based tools

This section explains the API designed for scenarios that require enhanced flexibility and customized behavior.
With this approach, you have full control over a tool, including its parameters, metadata, execution logic, and how it is registered and invoked.

This level of control is ideal for creating sophisticated tools that extend basic use cases, enabling seamless integration into agent sessions and workflows.

This page describes how to implement a tool, manage tools through registries, call them, and use within node-based agent architectures.

!!! note
    The API is multiplatform. This lets you use the same tools across different platforms.

## Tool implementation

The Koog framework provides the following approaches for implementing tools:

* Using the base class `Tool` for all tools. You should use this class when you need to return non-text results or require complete control over the tool behavior.
* Using the `SimpleTool` class that extends the base `Tool` class and simplifies the creation of tools that return text results. You should use this approach for scenarios where the 
  tool only needs to return a text.

Both approaches use the same core components but differ in implementation and the results they return.

### Tool class

The [`Tool<Args, Result>`](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/index.html) abstract class is the base class for creating tools in Koog.
It lets you create tools that accept specific argument types (`Args`) and return results of various types (`Result`).

Each tool consists of the following components:

| <div style="width:110px">Component</div> | Description                                                                                                                                                                                                                                                                                                                   |
|------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Args`                                   | The serializable data class that defines arguments required for the tool. This class must implement the [`ToolArgs`](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/-args/index.html) interface. For tools that do not require arguments, you can use the built-in `ToolArgs.Empty` implementation. |
| `Result`                                 | The type of result that the tool returns. This must implement the [`ToolResult`](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool-result/index.html) interface, which can be `ToolResult.Text`, `ToolResult.Boolean`, `ToolResult.Number`, or a custom implementation of `ToolResult.JSONSerializable`. |
| `argsSerializer`                         | The overridden variable that defines how the arguments for the tool are deserialized. See also [argsSerializer](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/args-serializer.html).                                                                                                                  |
| `descriptor`                             | The overridden variable that specifies tool metadata:<br/>- `name`<br/>- `description`<br/>- `requiredParameters` (empty by default)<br/>- `optionalParameters` (empty by default)<br/>See also [descriptor](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/descriptor.html).                        |
| `execute()`                              | The function that implements the logic of the tool. It takes arguments of type `Args` and returns a result of type `Result`. See also [execute()]().                                                                                                                                         |

!!! tip
    Ensure your tools have clear descriptions and well-defined parameter names to make it easier for the LLM to understand and use them properly.

#### Usage example

Here is an example of a custom tool implementation using the `Tool` class that returns a numeric result:

<!--- INCLUDE
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
-->
```kotlin
// Implement a simple calculator tool that adds two digits
object CalculatorTool : Tool<CalculatorTool.Args, Int>() {
    
    // Arguments for the calculator tool
    @Serializable
    data class Args(
        val digit1: Int,
        val digit2: Int
    ) {
        init {
            require(digit1 in 0..9) { "digit1 must be a single digit (0-9)" }
            require(digit2 in 0..9) { "digit2 must be a single digit (0-9)" }
        }
    }

    // Serializer for the Args class
    override val argsSerializer = Args.serializer()
    override val resultSerializer = Int.serializer()

    // Tool descriptor
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "calculator",
        description = "A simple calculator that can add two digits (0-9).",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "digit1",
                description = "The first digit to add (0-9)",
                type = ToolParameterType.Integer
            ),
            ToolParameterDescriptor(
                name = "digit2",
                description = "The second digit to add (0-9)",
                type = ToolParameterType.Integer
            )
        )
    )

    // Function to add two digits
    override suspend fun execute(args: Args): Int = args.digit1 + args.digit2
}
```
<!--- KNIT example-class-based-tools-01.kt --> 

After implementing your tool, you need to add it to a tool registry and then use it with an agent. For details, see [Tool registry](tools-overview.md#tool-registry).

For more details, see [API reference](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/index.html).

### SimpleTool class

The [`SimpleTool<Args>`](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-simple-tool/index.html) abstract class extends `Tool<Args, ToolResult.Text>` and simplifies the creation of tools that return text results.

Each simple tool consists of the following components:

| <div style="width:110px">Component</div> | Description                                                                                                                                                                                                                                                                                              |
|------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Args`                                   | The serializable data class that defines arguments required for the custom tool.                                                                                                                                                                                                                         |
| `argsSerializer`                         | The overridden variable that defines how the arguments for the tool are serialized. See also [argsSerializer](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/args-serializer.html).                                                                                             |
| `descriptor`                             | The overridden variable that specifies tool metadata:<br/>- `name`<br/>- `description`<br/>- `requiredParameters` (empty by default)<br/> - `optionalParameters` (empty by default)<br/> See also [descriptor](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/descriptor.html). |
| `doExecute()`                            | The overridden function that describes the main action performed by the tool. It takes arguments of type `Args` and returns a `String`. See also [doExecute()](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-simple-tool/do-execute.html).                                          |


!!! tip
    Ensure your tools have clear descriptions and well-defined parameter names to make it easier for the LLM to understand and use them properly.

#### Usage example 

Here is an example of a custom tool implementation using `SimpleTool`:

<!--- INCLUDE
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable
-->
```kotlin
// Create a tool that casts a string expression to a double value
object CastToDoubleTool : SimpleTool<CastToDoubleTool.Args>() {
    // Define tool arguments
    @Serializable
    data class Args(val expression: String, val comment: String)

    // Serializer for the Args class
    override val argsSerializer = Args.serializer()

    // Tool descriptor
    override val descriptor = ToolDescriptor(
        name = "cast_to_double",
        description = "casts the passed expression to double or returns 0.0 if the expression is not castable",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "expression", description = "An expression to case to double", type = ToolParameterType.String
            )
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "comment",
                description = "A comment on how to process the expression",
                type = ToolParameterType.String
            )
        )
    )
    
    // Function that executes the tool with the provided arguments
    override suspend fun doExecute(args: Args): String {
        return "Result: ${castToDouble(args.expression)}, " + "the comment was: ${args.comment}"
    }
    
    // Function to cast a string expression to a double value
    private fun castToDouble(expression: String): Double {
        return expression.toDoubleOrNull() ?: 0.0
    }
}
```
<!--- KNIT example-class-based-tools-02.kt --> 

After implementing your tool, you need to add it to a tool registry and then use it with an agent.
For details, see [Tool registry](tools-overview.md#tool-registry).
