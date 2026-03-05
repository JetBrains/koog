import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.ollama.client.OllamaModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;

public class ExampleQuickstartOllama {
    public static void main(String[] args) {
        // Create an agent
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOllamaAIExecutor())
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .build();

        // Run the agent
        String result = agent.run("Hello! How can you help me?");
        System.out.println(result);
    }
}
