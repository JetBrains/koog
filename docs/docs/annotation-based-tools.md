# Annotation-based tools

Annotation-based tools provide a declarative way to expose functions as tools for large language models (LLMs).
By using annotations, you can transform any function into a tool that LLMs can understand and use.

This approach is useful when you need to expose existing functionality to LLMs without implementing tool descriptions manually.

!!! note
    Annotation-based tools are JVM-only and not available for other platforms. For multiplatform support, use the [class-based tool API](class-based-tools.md).

## Key annotations

To start using annotation-based tools in your project, you need to understand the following key annotations:

| Annotation        | Description                                                             |
|-------------------|-------------------------------------------------------------------------|
| `@Tool`           | Marks functions that should be exposed as tools to LLMs.                |
| `@LLMDescription` | Provides descriptive information about your tools and their components. |


## @Tool annotation

The `@Tool` annotation is used to mark functions that should be exposed as tools to LLMs.
The functions annotated with `@Tool` are collected by reflection from objects that implement the `ToolSet` interface. For details, see [Implement the ToolSet interface](#implement-the-toolset-interface).

### Definition

<!--- INCLUDE
-->
```kotlin
@Target(AnnotationTarget.FUNCTION)
public annotation class Tool(val customName: String = "")
```
<!--- KNIT example-annotation-based-tools-01.kt -->

### Parameters

| <div style="width:100px">Name</div> | Required | Description                                                                              |
|-------------------------------------|----------|------------------------------------------------------------------------------------------|
| `customName`                        | No       | Specifies a custom name for the tool. If not provided, the name of the function is used. |

### Usage

To mark a function as a tool, apply the `@Tool` annotation to this function in a class that implements the `ToolSet` interface:
<!--- INCLUDE
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
-->
```kotlin
class MyToolSet : ToolSet {
    @Tool
    fun myTool(): String {
        // Tool implementation
        return "Result"
    }

    @Tool(customName = "customToolName")
    fun anotherTool(): String {
        // Tool implementation
        return "Result"
    }
}
```
<!--- KNIT example-annotation-based-tools-02.kt -->

## @LLMDescription annotation

The `@LLMDescription` annotation provides descriptive information about code elements (classes, functions, parameters, and so on) to LLMs.
This helps LLMs understand the purpose and usage of these elements.

### Definition

<!--- INCLUDE
-->
```kotlin
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION
)
public annotation class LLMDescription(val description: String)
```
<!--- KNIT example-annotation-based-tools-03.kt -->

### Parameters

| Name          | Required | Description                                    |
|---------------|----------|------------------------------------------------|
| `description` | Yes      | A string that describes the annotated element. |


### Usage

The `@LLMDescription` annotation can be applied at various levels. For example:

* Function level:
<!--- INCLUDE
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
-->
```kotlin
@Tool
@LLMDescription("Performs a specific operation and returns the result")
fun myTool(): String {
    // Function implementation
    return "Result"
}
```
<!--- KNIT example-annotation-based-tools-04.kt -->

* Parameter level:

<!--- INCLUDE
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
-->
```kotlin
@Tool
@LLMDescription("Processes input data")
fun processTool(
    @LLMDescription("The input data to process")
    input: String,

    @LLMDescription("Optional configuration parameters")
    config: String = ""
): String {
    // Function implementation
    return "Processed: $input with config: $config"
}
```
<!--- KNIT example-annotation-based-tools-05.kt -->

## Creating a tool

### 1. Implement the ToolSet interface

Create a class that implements the [`ToolSet`](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools.reflect/-tool-set/index.html) interface.
This interface marks your class as a container for tools.

<!--- INCLUDE
import ai.koog.agents.core.tools.reflect.ToolSet
-->
```kotlin
class MyFirstToolSet : ToolSet {
    // Tools will go here
}
```
<!--- KNIT example-annotation-based-tools-06.kt -->

### 2. Add tool functions

Add functions to your class and annotate them with `@Tool` to expose them as tools:

<!--- INCLUDE
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
-->
```kotlin
class MyFirstToolSet : ToolSet {
    @Tool
    fun getWeather(location: String): String {
        // In a real implementation, you would call a weather API
        return "The weather in $location is sunny and 72°F"
    }
}
```
<!--- KNIT example-annotation-based-tools-07.kt -->

### 3. Add descriptions

Add `@LLMDescription` annotations to provide context for the LLM:
<!--- INCLUDE
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
-->
```kotlin
@LLMDescription("Tools for getting weather information")
class MyFirstToolSet : ToolSet {
    @Tool
    @LLMDescription("Get the current weather for a location")
    fun getWeather(
        @LLMDescription("The city and state/country")
        location: String
    ): String {
        // In a real implementation, you would call a weather API
        return "The weather in $location is sunny and 72°F"
    }
}
```
<!--- KNIT example-annotation-based-tools-08.kt -->

### 4. Use your tools with an agent

Now you can use your tools with an agent:
<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.example.exampleAnnotationBasedTools06.MyFirstToolSet
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

const val apiToken = ""
-->
```kotlin
fun main() {
    runBlocking {
        // Create your tool set
        val weatherTools = MyFirstToolSet()

        // Create an agent with your tools

        val agent = AIAgent(
            promptExecutor = simpleOpenAIExecutor(apiToken),
            systemPrompt = "Provide weather information for a given location.",
            llmModel = OpenAIModels.Chat.GPT4o,
            toolRegistry = ToolRegistry {
                tools(weatherTools)
            }
        )

        // The agent can now use your weather tools
        agent.run("What's the weather like in New York?")
    }
}
```
<!--- KNIT example-annotation-based-tools-09.kt -->

## Usage examples

Here are some real-world examples of tool annotations.

### Basic example: Switch controller

This example shows a simple tool set for controlling a switch:
<!--- INCLUDE
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool

class Switch(private var state: Boolean) {
    fun switch(state: Boolean) {
        this.state = state
    }
    
    fun isOn(): Boolean {
        return state
    }
}
-->
```kotlin
@LLMDescription("Tools for controlling a switch")
class SwitchTools(val switch: Switch) : ToolSet {
    @Tool
    @LLMDescription("Switches the state of the switch")
    fun switch(
        @LLMDescription("The state to set (true for on, false for off)")
        state: Boolean
    ): String {
        switch.switch(state)
        return "Switched to ${if (state) "on" else "off"}"
    }

    @Tool
    @LLMDescription("Returns the current state of the switch")
    fun switchState(): String {
        return "Switch is ${if (switch.isOn()) "on" else "off"}"
    }
}
```
<!--- KNIT example-annotation-based-tools-10.kt -->

When an LLM needs to control a switch, it can understand the following information from the provided description:

- The purpose and functionality of the tools.
- The required parameters for using the tools.
- The acceptable values for each parameter.
- The expected return values upon execution.

### Advanced example: Diagnostic tools

This example shows a more complex tool set for device diagnostics:
<!--- INCLUDE
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
-->
```kotlin
@LLMDescription("Tools for performing diagnostics and troubleshooting on devices")
class DiagnosticToolSet : ToolSet {
    @Tool
    @LLMDescription("Run diagnostic on a device to check its status and identify any issues")
    fun runDiagnostic(
        @LLMDescription("The ID of the device to diagnose")
        deviceId: String,

        @LLMDescription("Additional information for the diagnostic (optional)")
        additionalInfo: String = ""
    ): String {
        // Implementation
        return "Diagnostic results for device $deviceId"
    }

    @Tool
    @LLMDescription("Analyze an error code to determine its meaning and possible solutions")
    fun analyzeError(
        @LLMDescription("The error code to analyze (e.g., 'E1001')")
        errorCode: String
    ): String {
        // Implementation
        return "Analysis of error code $errorCode"
    }
}
```
<!--- KNIT example-annotation-based-tools-11.kt -->

## Best practices

* **Provide clear descriptions**: write clear, concise descriptions that explain the purpose and behavior of tools, parameters, and return values.
* **Describe all parameters**: add `@LLMDescription` to all parameters to help LLMs understand what each parameter is for.
* **Use consistent naming**: use consistent naming conventions for tools and parameters to make them more intuitive.
* **Group related tools**: group related tools in the same `ToolSet` implementation and provide a class-level description.
* **Return informative results**: make sure tool return values provide clear information about the result of the operation.
* **Handle errors gracefully**: include error handling in your tools and return informative error messages.
* **Document default values**: when parameters have default values, document this in the description.
* **Keep tools focused**: Each tool should perform a specific, well-defined task rather than trying to do too many things.

## Troubleshooting common issues

When working with tool annotations, you might encounter some common issues.

### Tools not being recognized

If the agent does not recognize your tools, check the following:

- Your class implements the `ToolSet` interface.
- All tool functions are annotated with `@Tool`.
- Tool functions have appropriate return types (`String` is recommended for simplicity).
- Your tools are properly registered with the agent.

### Unclear tool descriptions

If the LLM does not use your tools correctly or misunderstands their purpose, try the following:

- Improve your `@LLMDescription` annotations to be more specific and clear.
- Include examples in your descriptions if appropriate.
- Specify parameter constraints in the descriptions (for example, `"Must be a positive number"`).
- Use consistent terminology throughout your descriptions.

### Parameter type issues

If the LLM provides incorrect parameter types, try the following:

- Use simple parameter types when possible (`String`, `Boolean`, `Int`).
- Clearly describe the expected format in the parameter description.
- For complex types, consider using `String` parameters with a specific format and parse them in your tool.
- Include examples of valid inputs in your parameter descriptions.

### Performance issues

If your tools cause performance problems, try the following:

- Keep tool implementations lightweight.
- For resource-intensive operations, consider implementing asynchronous processing.
- Cache results when appropriate.
- Log tool usage to identify bottlenecks.
