# ToolDescriptorSchemer

## What is it

`ToolDescriptorSchemer` is a extension point that converts a `ToolDescriptor` into a JSON Schema object compatible with specific LLM providers.

Key points:

- Location: `ai.koog.agents.core.tools.serialization.ToolDescriptorSchemer`
- Contract: a single function `scheme(toolDescriptor: ToolDescriptor): JsonObject`
- Implementations provided:
  - `OpenAICompatibleToolDescriptorSchemer` — generates schemas compatible with OpenAI‑style function/tool definitions.
  - `OllamaToolDescriptorSchemer` — generates schemas compatible with Ollama tool JSON.

```kotlin
// Interface
interface ToolDescriptorSchemaGenerator {
  fun generate(toolDescriptor: ToolDescriptor): JsonObject
}
```

## Why to use it?
If you want to provide custom scheme for existing or new LLM providers, implement this interface to convert Koog’s `ToolDescriptor` into the expected JSON Schema format.

## Implementation example

Below is a minimal custom implementation that renders only a subset of parameter types to illustrate how to plug into the SPI. Real implementations should cover all `ToolParameterType`s (String, Integer, Float, Boolean, Null, Enum, List, Object, AnyOf).

<!--- INCLUDE
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.serialization.ToolDescriptorSchemer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
-->
```kotlin
class MinimalSchemer : ToolDescriptorSchemaGenerator {
    override fun generate(toolDescriptor: ToolDescriptor): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            (toolDescriptor.requiredParameters + toolDescriptor.optionalParameters).forEach { p ->
                put(p.name, buildJsonObject {
                    put("description", p.description)
                    when (val t = p.type) {
                        ToolParameterType.String -> put("type", "string")
                        ToolParameterType.Integer -> put("type", "integer")
                        is ToolParameterType.Enum -> {
                            put("type", "string")
                            putJsonArray("enum") { t.entries.forEach { add(it) } }
                        }
                        else -> put("type", "string") // fallback for brevity
                    }
                })
            }
        }
        putJsonArray("required") { toolDescriptor.requiredParameters.forEach { add(it.name) } }
    }
}
```
<!--- KNIT example-tool-descriptor-schemer-01.kt -->

## Example of usage with client

Typically you do not need to call a schemer directly. Koog clients accept a list of `ToolDescriptor` objects and apply the correct schemer internally when serializing requests for the provider.

The example below defines a simple tool and passes it to the OpenAI client. The client will use `OpenAICompatibleToolDescriptorSchemer` under the hood to build the JSON schema.

<!--- INCLUDE
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.llm.LLModel
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType

class MinimalSchemer : ToolDescriptorSchemaGenerator {
    override fun generate(toolDescriptor: ToolDescriptor): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            (toolDescriptor.requiredParameters + toolDescriptor.optionalParameters).forEach { p ->
                put(p.name, buildJsonObject {
                    put("description", p.description)
                    when (val t = p.type) {
                        ToolParameterType.String -> put("type", "string")
                        ToolParameterType.Integer -> put("type", "integer")
                        is ToolParameterType.Enum -> {
                            put("type", "string")
                            putJsonArray("enum") { t.entries.forEach { add(it) } }
                        }
                        else -> put("type", "string") // fallback for brevity
                    }
                })
            }
        }
        putJsonArray("required") { toolDescriptor.requiredParameters.forEach { add(it.name) } }
    }
}

-->
```kotlin
val client = OpenAILLMClient(apiKey = System.getenv("OPENAI_API_KEY"), toolsConverter = MinimalSchemer())

val getUserTool = ToolDescriptor(
    name = "get_user",
    description = "Returns user profile by id",
    requiredParameters = listOf(
        ToolParameterDescriptor(
            name = "id",
            description = "User id",
            type = ToolParameterType.String
        )
    )
)

val prompt = Prompt.text("Find the user named Alice and summarize her profile.")

val responses = client.execute(
    prompt = prompt,
    model = LLModel("gpt-4o-mini"),
    tools = listOf(getUserTool) // <-- pass ToolDescriptor(s)
)
```
<!--- KNIT example-tool-descriptor-schemer-02.kt -->

If you need direct access to the produced schema (for debugging or for a custom transport), you can instantiate the provider‑specific schemer and serialize the JSON yourself:

<!--- INCLUDE
import kotlinx.serialization.json.Json
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemer
-->

fun getUserTool(): ToolDescriptor {
    return ToolDescriptor(
        name = "get_user",
        description = "Returns user profile by id",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "id",
                description = "User id",
                type = ToolParameterType.String
            )
        )
    )
}
```kotlin
val json = Json { prettyPrint = true }
val schema = OpenAICompatibleToolDescriptorSchemer().scheme(getUserTool)
println(json.encodeToString(JsonObject.serializer(), schema))
```
<!--- KNIT example-tool-descriptor-schemer-03.kt -->
