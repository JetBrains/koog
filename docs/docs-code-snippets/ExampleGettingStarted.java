import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.ollama.client.OllamaModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;

public class GettingStarted {
    public static void main(String[] args) throws Exception {
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .build();

        System.out.println(agent.run("Hello! How can you help me?"));
    }
}
