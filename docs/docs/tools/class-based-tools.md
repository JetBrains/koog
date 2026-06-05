# Class-based tools

This section explains the API designed for scenarios that require enhanced flexibility and customized behavior.
With this approach in Kotlin, you have full control over a tool, including its parameters, metadata, execution logic, and how it is registered and invoked. In Java, tools are created using annotation-based methods with reflection-based registration.

This level of control is ideal for creating sophisticated tools that extend basic use cases, enabling seamless integration into agent sessions and workflows.

This page describes how to implement a tool in both Kotlin and Java, manage tools through registries, call them, and use within node-based agent architectures.

!!! note
    The API is multiplatform for Kotlin. Java tools are implemented using annotation-based methods and registered via reflection. This lets you use the same tools across different platforms in Kotlin, while Java provides full JVM interoperability.

## Tool implementation

The Koog framework provides the following approaches for implementing tools:

For Kotlin:

* Using the base class `Tool` for all tools. You should use this class when you need to return non-text results or require complete control over the tool behavior.
* Using the `SimpleTool` class that extends the base `Tool` class and simplifies the creation of tools that return text results. You should use this approach for scenarios where the 
  tool only needs to return a text.

Both approaches use the same core components but differ in implementation and the results they return.

For Java:

* Using annotation-based methods (`@Tool` and `@LLMDescription`) with reflection-based registration. This is the recommended approach for Java interoperability, as subclassing Kotlin's `Tool` or `SimpleTool` from Java is not supported due to suspend function limitations.

### Tool class (Kotlin)

The [`Tool<Args, Result>`](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/index.html) abstract class is the base class for creating tools in Kotlin.
It lets you create tools that accept specific argument types (`Args`) and return results of various types (`Result`).

Each tool consists of the following components:

| <div style="width:110px">Component</div> | Description                                                                                                                                                                                                                                                                                                                                                                                                                           |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Args`                                   | The serializable data class that defines arguments required for the tool.                                                                                                                                                                                                                                                                                                                                                             |
| `Result`                                 | The serializable type of result that the tool returns. If you want to present tool results in a custom format, please inherit [ToolResult.TextSerializable](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool-result/-text-serializable/index.html) class and implement `textForLLM(): String` method                                                                                                           |
| `argsSerializer`                         | The overridden variable that defines how the arguments for the tool are deserialized. See also [argsSerializer](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/args-serializer.html).                                                                                                                                                                                                                        |
| `resultSerializer`                       | The overridden variable that defines how the result of the tool is deserialized. See also [resultSerializer](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/result-serializer.html). If you chose to inherit [ToolResult.TextSerializable](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool-result/-text-serializable/index.html) consider using `ToolResultUtils.toTextSerializer()` |
| `descriptor`                             | The overridden variable that specifies tool metadata:<br/>- `name`<br/>- `description`<br/>- `requiredParameters` (empty by default)<br/>- `optionalParameters` (empty by default)<br/>See also [descriptor](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/descriptor.html).                                                                                                                                |
| `execute()`                              | The function that implements the logic of the tool. It takes arguments of type `Args` and returns a result of type `Result`. See also [execute()]().                                                                                                                                                                                                                                                                                  |

!!! note "Java Implementation"
    In Java, instead of subclassing `Tool<Args, Result>`, use annotation-based methods with `@Tool` and `@LLMDescription`. The framework handles serialization and registration automatically through reflection. For more
    details, see [Annotation-based methods](#annotation-based-methods-java) below.

!!! tip
    Ensure your tools have clear descriptions and well-defined parameter names to make it easier for the LLM to understand and use them properly. In Kotlin, use the `descriptor` property; in Java, use `@LLMDescription` annotations.

#### Usage example

Here is an example of a custom tool implementation using the `Tool` class that returns a numeric result:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.Tool
    import ai.koog.agents.core.tools.ToolDescriptor
    import ai.koog.agents.core.tools.ToolParameterDescriptor
    import ai.koog.agents.core.tools.ToolParameterType
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    import ai.koog.agents.core.tools.annotations.LLMDescription
    -->
    ```kotlin
    // Implement a simple calculator tool that adds two digits
    object CalculatorTool : Tool<CalculatorTool.Args, Int>(
        argsType = typeToken<Args>(),
        resultType = typeToken<Int>(),
        name = "calculator",
        description = "A simple calculator that can add two digits (0-9)."
    ) {

        // Arguments for the calculator tool
        @Serializable
        data class Args(
            @property:LLMDescription("The first digit to add (0-9)")
            val digit1: Int,
            @property:LLMDescription("The second digit to add (0-9)")
            val digit2: Int
        ) {
            init {
                require(digit1 in 0..9) { "digit1 must be a single digit (0-9)" }
                require(digit2 in 0..9) { "digit2 must be a single digit (0-9)" }
            }
        }

        // Function to add two digits
        override suspend fun execute(args: Args): Int = args.digit1 + args.digit2
    }
    ```
    <!--- KNIT example-class-based-tools-01.kt -->

After implementing your tool, you need to add it to a tool registry and then use it with an agent. For details, see [Tool registry](../tools/index.md#tool-registry).

For more details, see [API reference](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/index.html).

#### Reading the agent context from a tool

Tools that need the agent's full state (LLM context, run id, configuration, storage, ...) extend `AgentContextAwareTool<Args, Result>` instead of `Tool<Args, Result>`. The framework injects the live `AIAgentContext` driving the call, and the tool receives it as a typed parameter rather than reading it out of the argument schema.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.context.AIAgentContext
    import ai.koog.agents.core.agent.tools.AgentContextAwareTool
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    -->
    ```kotlin
    // A tool that reads the live AIAgentContext driving the call.
    object TracingCalculatorTool : AgentContextAwareTool<TracingCalculatorTool.Args, Int>(
        argsType = typeToken<Args>(),
        resultType = typeToken<Int>(),
        name = "tracing_calculator",
        description = "Adds two digits and emits a log line tagged with the agent run id."
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("The first digit to add (0-9)")
            val digit1: Int,
            @property:LLMDescription("The second digit to add (0-9)")
            val digit2: Int
        )

        override suspend fun execute(args: Args, context: AIAgentContext): Int {
            val runId = context.runId
            // ... use runId for cross-cutting context (logging, tracing, correlation)
            return args.digit1 + args.digit2
        }
    }
    ```
    <!--- KNIT example-class-based-tools-metadata-01.kt -->

`AgentContextAwareTool` is dispatched by the framework via a per-call `ToolCallMetadata` side channel that the framework manages on the tool's behalf. Invoking such a tool outside an agent run throws `IllegalStateException` because no `AIAgentContext` was injected; production code should always go through `ContextualAgentEnvironment`, and unit tests can supply the context explicitly via `ToolCallMetadata.of(AgentContextAwareTool.AgentContextKey to context)`.

#### Reading raw per-call metadata

A small number of tools want to read caller- or feature-contributed entries that are *not* the agent context (for example a distributed-tracing span id contributed by an observability feature). These tools extend `ToolBase<Args, Result>` directly, which exposes the full `ToolCallMetadata` bag:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.ToolBase
    import ai.koog.agents.core.tools.ToolCallMetadata
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    -->
    ```kotlin
    object SpanAwareCalculatorTool : ToolBase<SpanAwareCalculatorTool.Args, Int>(
        argsType = typeToken<Args>(),
        resultType = typeToken<Int>(),
        name = "span_aware_calculator",
        description = "Adds two digits, propagating a tracing span id from caller or feature metadata."
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("The first digit to add (0-9)")
            val digit1: Int,
            @property:LLMDescription("The second digit to add (0-9)")
            val digit2: Int
        )

        override suspend fun execute(args: Args, metadata: ToolCallMetadata): Int {
            val traceSpanId = metadata["trace.span.id"] as? String
            // ... use traceSpanId for cross-cutting context (logging, tracing, correlation)
            return args.digit1 + args.digit2
        }
    }
    ```
    <!--- KNIT example-class-based-tools-metadata-02.kt -->

Callers can pass metadata through `SafeTool.execute(args, serializer, metadata)` or directly through `AIAgentEnvironment.executeTool(toolCall, metadata)`. Features can contribute metadata for every tool call during installation by calling `pipeline.provideToolCallMetadata(this) { eventContext -> mapOf(...) }`. Caller-supplied metadata always wins over feature contributions on key collision.

Existing tools that extend `Tool<Args, Result>` and override `execute(args)` continue to work unchanged: the framework dispatches them through the same path and discards any `ToolCallMetadata`. To opt in to metadata, switch to `AgentContextAwareTool` (typed context access) or `ToolBase` (raw bag access).

### SimpleTool class (Kotlin)

The [`SimpleTool<Args>`](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-simple-tool/index.html) abstract class extends `Tool<Args, ToolResult.Text>` and simplifies the creation of tools that return text results.

Each simple tool consists of the following components:

| <div style="width:110px">Component</div> | Description                                                                                                                                                                                                                                                                                              |
|------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Args`                                   | The serializable data class that defines arguments required for the custom tool.                                                                                                                                                                                                                         |
| `argsSerializer`                         | The overridden variable that defines how the arguments for the tool are serialized. See also [argsSerializer](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/args-serializer.html).                                                                                             |
| `descriptor`                             | The overridden variable that specifies tool metadata:<br/>- `name`<br/>- `description`<br/>- `requiredParameters` (empty by default)<br/> - `optionalParameters` (empty by default)<br/> See also [descriptor](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool/descriptor.html). |
| `doExecute()`                            | The overridden function that describes the main action performed by the tool. It takes arguments of type `Args` and returns a `String`. See also [doExecute()](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-simple-tool/do-execute.html).                                          |

!!! note "Java Implementation"
    In Java, the equivalent approach is to use annotation-based methods that return `String`. The framework automatically handles the text result wrapping. For more details, see [Annotation-based methods](#annotation-based-methods-java) below.

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

### Annotation-based methods (Java)

To implement tools in Java, instead of subclassing `Tool` or `SimpleTool`, use annotation-based methods with `@Tool` and
`@LLMDescription`. Koog handles serialization and registration automatically through reflection. To learn more about the
implementation, see Java examples below.

#### Usage examples

This is an example of a tool implementation in Java, equivalent to using the `Tool` class in Kotlin.

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // Java equivalent: implement the tool as a Java method and register it via ToolRegistry.builder().
    // This is the recommended Java interop path instead of subclassing the Kotlin Tool base class.
    public final class CalculatorTool {
        private CalculatorTool() {}
    
        @Tool(customName = "calculator")
        @LLMDescription(description = "A simple calculator that can add two digits (0-9).")
        public static int calculator(
                @LLMDescription(description = "The first digit to add (0-9)") int digit1,
                @LLMDescription(description = "The second digit to add (0-9)") int digit2
        ) {
            if (digit1 < 0 || digit1 > 9) throw new IllegalArgumentException("digit1 must be a single digit (0-9)");
            if (digit2 < 0 || digit2 > 9) throw new IllegalArgumentException("digit2 must be a single digit (0-9)");
            return digit1 + digit2;
        }
    
        public static ToolRegistry registry() throws NoSuchMethodException {
            return ToolRegistry.builder()
                .tool(CalculatorTool.class.getMethod("calculator", int.class, int.class))
                .build();
        }
    }
    // Note: Subclassing the Kotlin Tool<TArgs, TResult> and overriding a suspend execute(...) from Java is not supported.
    // The Java interop uses reflection-based registration of Java methods as tools.
    ```
    <!--- KNIT example-class-based-tools-java-01.java -->

Here is an example of a tool implementation in Java, equivalent to using the `SimpleTool` class in Kotlin. This example
implements a simple tool that returns a text result.

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // Java equivalent of SimpleTool: provide a Java method and register it as a tool.
    public final class CastToDoubleTool {
        private CastToDoubleTool() {}
    
        @Tool(customName = "cast_to_double")
        @LLMDescription(description = "casts the passed expression to double or returns 0.0 if the expression is not castable")
        public static String castToDouble(
                @LLMDescription(description = "An expression to case to double") String expression,
                @LLMDescription(description = "A comment on how to process the expression") String comment
        ) {
            double value;
            try {
                value = Double.parseDouble(expression);
            } catch (Exception e) {
                value = 0.0;
            }
            return "Result: " + value + ", the comment was: " + comment;
        }
    
        public static ToolRegistry registry() throws NoSuchMethodException {
            return ToolRegistry.builder()
                .tool(CastToDoubleTool.class.getMethod("castToDouble", String.class, String.class))
                .build();
        }
    }
    // Note: Extending Kotlin SimpleTool<TArgs> from Java is not required; registering a Java method is the idiomatic approach.
    ```
    <!--- KNIT example-class-based-tools-java-02.java -->

### Sending tool result to LLM in custom format

For Kotlin:

If you are not happy with JSON results sent to LLM (in some cases, LLMs can work better if tool output is structured as Markdown, for instance), you have to follow the following steps:

1. Implement `ToolResult.TextSerializable` interface, and override `textForLLM()` method
2. Override `resultSerializer` using `ToolResultUtils.toTextSerializer<T>()`

For Java:

Return formatted text (such as Markdown) directly as a `String` from your annotated method. The framework handles this automatically.

#### Example

Here is an example showing custom formatted output in both Kotlin and Java:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.Tool
    import ai.koog.agents.core.tools.ToolDescriptor
    import ai.koog.agents.core.tools.ToolParameterDescriptor
    import ai.koog.agents.core.tools.ToolParameterType
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.prompt.markdown.markdown
    -->
    ```kotlin
    // A tool that edits file
    object EditFile : Tool<EditFile.Args, EditFile.Result>(
        argsType = typeToken<Args>(),
        resultType = typeToken<Result>(),
        name = "edit_file",
        description = "Edits the given file"
    ) {
        // Define tool arguments
        @Serializable
        public data class Args(
            val path: String,
            val original: String,
            val replacement: String
        )

        @Serializable
        public data class Result(
            private val patchApplyResult: PatchApplyResult
        ) {

            @Serializable
            public sealed interface PatchApplyResult {
                @Serializable
                public data class Success(val updatedContent: String) : PatchApplyResult

                @Serializable
                public sealed class Failure(public val reason: String) : PatchApplyResult
            }

            // Textual output (in Markdown format) that will be visible to the LLM after the tool finishes.
            fun textForLLM(): String = markdown {
                if (patchApplyResult is PatchApplyResult.Success) {
                    line {
                        bold("Successfully").text(" edited file (patch applied)")
                    }
                } else {
                    line {
                        text("File was ")
                            .bold("not")
                            .text(" modified (patch application failed: ${(patchApplyResult as PatchApplyResult.Failure).reason})")
                    }
                }
            }

            override fun toString(): String = textForLLM()
        }

        // Function that executes the tool with the provided arguments
        override suspend fun execute(args: Args): Result {
            return TODO("Implement file edit")
        }
    }
    ```
    <!--- KNIT example-class-based-tools-03.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    import ai.koog.agents.core.tools.ToolRegistry;
    import ai.koog.agents.core.tools.annotations.LLMDescription;
    import ai.koog.agents.core.tools.annotations.Tool;

    // Java equivalent: return Markdown text directly to the LLM from a Java method and register it as a tool.
    // This avoids needing a custom serializable Result type (which would require Kotlin serialization support).
    public final class EditFile {
        private EditFile() {}

        @Tool(customName = "edit_file")
        @LLMDescription(description = "Edits the given file")
        public static String editFile(
                String path,
                String original,
                String replacement
        ) {
            // TODO: Implement file edit logic; below is a placeholder illustrating Markdown output
            boolean success = false;
            if (success) {
                return "**Successfully** edited file (patch applied)";
            } else {
                return "File was **not** modified (patch application failed: reason)";
            }
        }

        public static ToolRegistry registry() throws NoSuchMethodException {
            return ToolRegistry.builder()
                .tool(EditFile.class.getMethod("editFile", String.class, String.class, String.class))
                .build();
        }
    }
    // Note: If you need a structured custom Result object from Java, you must expose a Kotlin @Serializable type
    // or another serializer-aware type. Returning String works out-of-the-box with Koog's Java interop.
    ```
    <!--- KNIT example-class-based-tools-java-03.java -->

After implementing your tool in Kotlin or Java, you need to add it to a tool registry and then use it with an agent.
For details, see [Tool registry](../tools/index#tool-registry).
