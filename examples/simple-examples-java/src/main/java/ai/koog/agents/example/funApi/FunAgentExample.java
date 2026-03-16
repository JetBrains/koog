package ai.koog.agents.example.funApi;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor;
import ai.koog.prompt.executor.ollama.client.OllamaClient;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import ai.koog.prompt.message.Message;

import java.util.List;

/**
 * This example demonstrates how to create a simple functional agent using the Java API.
 * The agent sends a single request to the LLM and returns the response.
 */
public class FunAgentExample {

    public static void main(String[] args) throws Exception {
        try (SingleLLMPromptExecutor executor = new SingleLLMPromptExecutor(new OllamaClient())) {
            AIAgent<String, String> funcAgent = AIAgent.builder()
                    .promptExecutor(executor)
                    .llmModel(OllamaModels.Meta.LLAMA_3_2)
                    .systemPrompt("You're helpful librarian agent.")
                    .functionalStrategy("funStrategy", (AIAgentFunctionalContext context, String input) -> {
                        List<Message.Response> responses = context.requestLLMMultiple(input);
                        return ((Message.Assistant) responses.get(0)).getContent();
                    })
                    .build();

            String result = funcAgent.run("Give me a list of top 10 books of all time");
            System.out.println(result);
        }
    }
}
