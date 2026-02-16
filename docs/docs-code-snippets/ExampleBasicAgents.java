import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.ext.tool.SayToUser;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

public class ExampleBasicAgents {
    public static void main(String[] args) {
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")))
            .systemPrompt("You are a helpful assistant. Answer user questions concisely.")
            .llmModel(OpenAIModels.Chat.GPT4o)
            .temperature(0.7)
            .toolRegistry(
                ToolRegistry.builder()
                    .tool(SayToUser.INSTANCE)
                    .build()
            )
            .maxIterations(100)
            .build();

        String result = agent.run("Hello! How can you help me?");
    }
}
