import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import ai.koog.prompt.message.Message;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;

public class ExampleFunctionalAgentsMinimal {
    public static void main(String[] args) {
        AIAgent<String, String> mathAgent = AIAgent.builder()
                .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
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
    }
}
