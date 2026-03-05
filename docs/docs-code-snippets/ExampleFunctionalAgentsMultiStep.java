import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.prompt.executor.ollama.client.OllamaClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import ai.koog.prompt.message.Message;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;

public class ExampleFunctionalAgentsMultiStep {
    public static void main(String[] args) {
        // Create an AIAgent instance using the builder
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
        // Run the agent with a user input and print the result
        String result = mathAgent.run("What is 12 × 9?");
        System.out.println(result);
    }
}
