# Functional agents

With functional agents, you implement the logic as a function that handles user input,
interacts with LLMs, calls tools if necessary, and produces the final output.
Compared to [graph-based agents](graph-based-agents.md),
this usually means faster prototyping with the following downsides:

- Not easy to visualize
- No state persistence

??? note "Prerequisites"

    --8<-- "quickstart-snippets.md:prerequisites"

    --8<-- "quickstart-snippets.md:dependencies"

    --8<-- "quickstart-snippets.md:api-key"

    Examples on this page assume that you are running Llama 3.2 locally via Ollama.

This page describes how to implement a functional strategy to quickly prototype some custom logic for your agent.

## Create a minimal functional agent

To create a minimal functional agent, use the same [`AIAgent`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent/-a-i-agent/index.html) interface as for a [basic agent](basic-agents.md) and pass an 
instance of [`AIAgentFunctionalStrategy`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent/-a-i-agent-functional-strategy/index.html) to it.
You can define a functional strategy that expects an input and returns an output, makes one LLM call, then returns the 
content of the assistant message from the response.

In Kotlin, the most convenient way is to use the `functionalStrategy {...}` DSL method. In Java, you can use the `functionalStrategy` method on the `AIAgent` builder.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.agent.functionalStrategy
    import ai.koog.prompt.message.MessagePart
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    val strategy = functionalStrategy<String, String> { input ->
        val response = requestLLM(input)
        response.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
    }

    val mathAgent = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(),
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        strategy = strategy
    )

    fun main() = runBlocking {
        val result = mathAgent.run("What is 12 × 9?")
        println(result)
    }
    ```
    <!--- KNIT example-functional-agent-01.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    AIAgent<String, String> mathAgent = AIAgent.builder()
        .promptExecutor(SimpleLLMExecutorsKt.simpleOllamaAIExecutor("http://localhost:11434"))
        .llmModel(OllamaModels.Meta.LLAMA_3_2)
        .functionalStrategy("mathStrategy", (AIAgentFunctionalContext context, String input) -> {
            Message.Response response = context.requestLLM(input);
            if (response instanceof Message.Assistant) {
                return ((Message.Assistant) response).getContent();
            }
            return "";
        })
        .build();

    String result = mathAgent.run("What is 12 × 9?");
    System.out.println(result);
    ```
   <!--- KNIT example-functional-agent-java-01.java -->

The agent can produce the following output:

```text
The answer to 12 × 9 is 108.
```
<!--- KNIT example-functional-agent-01.txt -->

## Make sequential LLM calls

You can extend the previous strategy to make multiple sequential LLM calls:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.functionalStrategy
    import ai.koog.prompt.message.Message
    import ai.koog.prompt.message.MessagePart
    -->
    ```kotlin
    fun Message.Assistant.text(): String =
        parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }

    val strategy = functionalStrategy<String, String> { input ->
        // The first LLM call produces an initial draft based on the user input
        val draft = requestLLM("Draft: $input").text()
        // The second LLM call improves the initial draft
        val improved = requestLLM("Improve and clarify.").text()
        // The final LLM call formats the improved text and returns the result
        requestLLM("Format the result as bold.").text()
    }
    ```
    <!--- KNIT example-functional-agent-02.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    AIAgent<String, String> mathAgent = AIAgent.builder()
        .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
        .systemPrompt("You are a precise math assistant.")
        .llmModel(OllamaModels.Meta.LLAMA_3_2)
        .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
            // The first LLM call produces an initial draft based on the user input
            Message.Response draftResponse = context.requestLLM("Draft: " + input);
            String draft = "";
            if (draftResponse instanceof Message.Assistant) {
                draft = ((Message.Assistant) draftResponse).getContent();
            }

            // The second LLM call improves the initial draft
            Message.Response improvedResponse = context.requestLLM("Improve and clarify.");
            String improved = "";
            if (improvedResponse instanceof Message.Assistant) {
                improved = ((Message.Assistant) improvedResponse).getContent();
            }

            // The final LLM call formats the improved text and returns the result
            Message.Response finalResponse = context.requestLLM("Format the result as bold.");
            if (finalResponse instanceof Message.Assistant) {
                return ((Message.Assistant) finalResponse).getContent();
            }
            return "";
        })
        .build();
    ```
    <!--- KNIT example-functional-agent-java-02.java -->

The agent can produce the following output:

```text
To calculate the product of 12 and 9, we multiply these two numbers together.

12 × 9 = **108**
```
<!--- KNIT example-functional-agent-02.txt -->

## Add tools

In many cases, a functional agent needs to complete specific tasks,
such as reading and writing data, calling APIs, or performing other deterministic operations.
In Koog, you expose such capabilities as [tools](../tools/index.md) and let the LLM decide when to call them.

Here is what you need to do:

1. Create an [annotation-based tool](../tools/annotation-based-tools.md).
2. Add it to a tool registry and pass the registry to the agent.
3. Make sure the agent strategy can identify tool calls in LLM responses, execute the requested tools,
   send their results back to the LLM, and repeat the process until there are no tool calls remaining.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.agent.functionalStrategy
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.agents.core.tools.annotations.Tool
    import ai.koog.agents.core.tools.reflect.ToolSet
    import ai.koog.prompt.message.MessagePart
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    @LLMDescription("Tools for performing math operations")
    class MathTools : ToolSet {
        @Tool
        @LLMDescription("Multiplies two numbers and returns the result")
        fun multiply(a: Int, b: Int): Int {
            // This is not necessary, but it helps to see the tool call in the console output
            println("Multiplying $a and $b...")
            return a * b
        }
    }

    val toolRegistry = ToolRegistry {
        tool(MathTools()::multiply)
    }

    val strategy = functionalStrategy<String, String> { input ->
        // Send the user input to the LLM
        var response = requestLLM(input)

        // Only loop while the LLM requests tools
        var toolCalls = response.parts.filterIsInstance<MessagePart.Tool.Call>()
        while (toolCalls.isNotEmpty()) {
            // Execute the tools and return the results
            val results = executeTools(toolCalls)
            // Send the tool results back to the LLM. The LLM may call more tools or return a final output
            response = sendToolResults(results)
            toolCalls = response.parts.filterIsInstance<MessagePart.Tool.Call>()
        }

        // When no tool calls remain, extract and return the assistant message content from the response
        response.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
    }

    val mathAgentWithTools = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(),
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        toolRegistry = toolRegistry,
        strategy = strategy
    )

    fun main() = runBlocking {
        val result = mathAgentWithTools.run("Multiply 3 by 4, then multiply the result by 5.")
        println(result)
    }
    ```
    <!--- KNIT example-functional-agent-03.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    @LLMDescription(description = "Tools for performing math operations")
    public static class MathTools implements ToolSet {
        @Tool
        @LLMDescription(description = "Multiplies two numbers and returns the result")
        public int multiply(int a, int b) {
            // This is not necessary, but it helps to see the tool call in the console output
            System.out.println("Multiplying " + a + " and " + b + "...");
            return a * b;
        }
    }

    public static void main(String[] args) {
        MathTools mathTools = new MathTools();
        ToolRegistry toolRegistry = ToolRegistry.builder()
            .tools(mathTools)
            .build();

        AIAgent<String, String> mathAgentWithTools = AIAgent.builder()
            .promptExecutor(SimpleLLMExecutorsKt.simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .toolRegistry(toolRegistry)
            .functionalStrategy("mathWithTools", (AIAgentFunctionalContext context, String input) -> {
                // Send the user input to the LLM
                List<Message.Response> responses = context.requestLLMMultiple(input);

                // Only loop while the LLM requests tools
                while (context.containsToolCalls(responses)) {
                    // Extract tool calls from the response
                    List<MessagePart.Tool.Call> pendingCalls = context.extractToolCalls(responses);
                    // Execute the tools and return the results
                    List<ReceivedToolResult> results = context.executeMultipleTools(pendingCalls, false);
                    // Send the tool results back to the LLM
                    responses = context.sendMultipleToolResults(results);
                }

                // Extract and return the assistant message content from the response
                Message.Response finalResponse = responses.get(0);
                if (finalResponse instanceof Message.Assistant) {
                    return ((Message.Assistant) finalResponse).getContent();
                }
                return "";
            })
            .build();

        String result = mathAgentWithTools.run("Multiply 3 by 4, then multiply the result by 5.");
        System.out.println(result);
    }
    ```
   <!--- KNIT example-functional-agent-java-03.java -->

The agent can produce the following output:

```text
Multiplying 3 and 4...
Multiplying 12 and 5...
The result of multiplying 3 by 4 is 12. Multiplying 12 by 5 gives us a final answer of 60.
```
<!--- KNIT example-functional-agent-03.txt -->

## Next steps

- Learn how to create [graph-based agents](graph-based-agents.md)
