import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.prompt.executor.ollama.client.OllamaClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import ai.koog.prompt.message.Message;
import kotlinx.serialization.json.Json;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;

public class ExampleFunctionalAgentsWithTools {
    // Define MathTools class (annotation-based tool)
    @LLMDescription(description = "Simple multiplier")
    public static class MathTools implements ToolSet {
        @Tool
        @LLMDescription(description = "Multiplies two numbers and returns the result")
        public int multiply(
                @LLMDescription(description = "First number") int a,
                @LLMDescription(description = "Second number") int b
        ) {
            return a * b;
        }
    }

    public static void main(String[] args) {
        // Create tool registry
        ToolRegistry toolRegistry = ToolRegistry.builder()
                .tools(new MathTools(), Json.Default)
                .build();

        // Create prompt executor
        OllamaClient ollamaClient = new OllamaClient("http://localhost:11434");
        MultiLLMPromptExecutor promptExecutor = new MultiLLMPromptExecutor(ollamaClient);

        // Create agent with functional strategy
        AIAgent<String, String> mathWithTools = AIAgent.builder()
                .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
                .systemPrompt("You are a precise math assistant. When multiplication is needed, use the multiplication tool.")
                .llmModel(OllamaModels.Meta.LLAMA_3_2)
                .toolRegistry(toolRegistry)
                .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                    // Send the user input to the LLM
                    Message.Response response = context.requestLLM(input, true);

                    // Loop while the LLM requests tools
                    int maxIterations = 10;
                    for (int i = 0; i < maxIterations && response instanceof Message.Tool.Call; i++) {
                        // Execute the tool call
                        Message.Tool.Call toolCall = (Message.Tool.Call) response;
                        ReceivedToolResult toolResult = context.executeTool(toolCall);

                        // Send the tool result back to the LLM
                        response = context.sendToolResult(toolResult);
                    }

                    // Extract and return the assistant message content
                    if (response instanceof Message.Assistant) {
                        return ((Message.Assistant) response).getContent();
                    }

                    return "Unexpected response type";
                })
                .build();

        // Run the agent with a user input and print the result
        String reply = mathWithTools.run("Please multiply 12 and 4, then add 10 to the result.");
        System.out.println(reply);

    }
}
