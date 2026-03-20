# Class-based tools

This section explains the API designed for scenarios that require enhanced flexibility and customized behavior.
With this approach, you have full control over a tool, including its parameters, metadata, execution logic, and how the result is represented to the LLM.

This level of control is ideal for creating sophisticated tools that extend basic use cases.

This page describes how to implement a tool in both Kotlin and Java, manage tools through registries, call them, and use within node-based agent architectures.

!!! note
    This API is multiplatform for Kotlin.

## Tool implementation

The Koog framework provides the following approaches for implementing tools:

=== "Kotlin"

    * Using the base class `Tool` for all tools. You should use this class when you need to return non-text results or require complete control over the tool behavior.
    * Using the `SimpleTool` class that extends the base `Tool` class and simplifies the creation of tools that return text results. You should use this approach for scenarios where the 
      tool only needs to return a text.

    Both approaches use the same core components but differ in implementation and the results they return.

=== "Java"

    Using the base class `JavaTool` for all tools.

    To avoid blocking the agent thread, tools are executed on a separate `Executor`.
    By default, each `JavaTool` instance uses its own single-threaded executor.
    You can set your own executor by using the constructor that takes an `Executor` parameter.


!!! tip
    Ensure your tools have clear descriptions and well-defined parameter names to make it easier for the LLM to understand and use them properly. In Kotlin, use the `descriptor` property; in Java, use `@LLMDescription` annotations.

#### Usage example

Here is an example of a custom tool implementation that returns a numeric result:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.Tool
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.serialization.JSONSerializer
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    -->
    ```kotlin
    // Implements a simple calculator tool that adds two numbers
    object CalculatorTool : Tool<CalculatorTool.Args, Int>(
        argsType = typeToken<Args>(),
        resultType = typeToken<Int>(),
        name = "calculator",
        description = "A simple calculator that can add two integers"
    ) {

        // Arguments for the calculator tool
        @Serializable
        data class Args(
            @property:LLMDescription("The first integer to add")
            val number1: Int,
            @property:LLMDescription("The second integer to add")
            val number2: Int
        )

        // Function to add two numbers
        override suspend fun execute(args: Args): Int = args.number1 + args.number2

        // Optional custom result string representation for the LLM
        override fun encodeResultToString(result: Int, serializer: JSONSerializer): String {
            return "Calculation result: $result"
        }
    }
    ```
    <!--- KNIT example-class-based-tools-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.JavaTool;
    import ai.koog.agents.core.tools.annotations.LLMDescription;
    import ai.koog.serialization.JSONSerializer;
    import ai.koog.serialization.TypeToken;
    import com.fasterxml.jackson.annotation.JsonProperty;
    import java.util.concurrent.Executors;
    -->
    ```java
    // Implements a simple calculator tool that adds two numbers
    class CalculatorTool extends JavaTool<CalculatorTool.Args, Integer> {
        public CalculatorTool() {
            super(
                TypeToken.of(Args.class),
                TypeToken.of(Integer.class),
                "calculator",
                "A simple calculator that can add two integers",
                // Optional: set your own executor that runs the tool
                Executors.newSingleThreadExecutor() 
            );
        }

        // Arguments for the calculator tool
        record Args(
            @JsonProperty("number1")
            @LLMDescription("The first integer to add")
            int number1,
            @JsonProperty("number2")
            @LLMDescription("The second integer to add")
            int number2
        ) {}
        
        // Function to add two numbers
        @Override
        public Integer execute(Args args) {
            return args.number1 + args.number2;
        }

        // Optional custom result string representation for the LLM
        @Override
        public String encodeResultToString(Integer result, JSONSerializer serializer) {
            return "Calculation result: " + result;
        }
    }
    ```
    <!--- KNIT exampleClassBasedToolsJava01.java -->

After implementing your tool, you need to add it to a tool registry and then use it with an agent. For details, see [Tool registry](tools-overview.md#tool-registry).

For more details, see [API reference](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/index.html).

### SimpleTool class (Kotlin)

The [`SimpleTool<Args>`](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-simple-tool/index.html) abstract class extends `Tool<Args, ToolResult.Text>` and simplifies the creation of tools that return text results.

!!! tip
    Ensure your tools have clear descriptions and well-defined parameter names to make it easier for the LLM to understand and use them properly. In Kotlin, use the `descriptor` and constructor parameters; in Java, use `@Tool` and `@LLMDescription` annotations.

#### Usage example 

Here is an example of a custom tool implementation using `SimpleTool` in Kotlin:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.SimpleTool
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    -->
    ```kotlin
    // Create a tool that casts a string expression to a double value
    object CastToDoubleTool : SimpleTool<CastToDoubleTool.Args>(
        argsType = typeToken<Args>(),
        name = "cast_to_double",
        description = "casts the passed expression to double or returns 0.0 if the expression is not castable"
    ) {
        // Define tool arguments
        @Serializable
        data class Args(
            @property:LLMDescription("An expression to case to double")
            val expression: String,
            @property:LLMDescription("A comment on how to process the expression")
            val comment: String
        )

        // Function that executes the tool with the provided arguments
        override suspend fun execute(args: Args): String {
            return "Result: ${castToDouble(args.expression)}, " + "the comment was: ${args.comment}"
        }

        // Function to cast a string expression to a double value
        private fun castToDouble(expression: String): Double {
            return expression.toDoubleOrNull() ?: 0.0
        }
    }
    ```
    <!--- KNIT example-class-based-tools-02.kt -->
