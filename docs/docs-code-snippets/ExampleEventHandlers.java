import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.prompt.executor.ollama.client.OllamaModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;

public class ExampleEventHandlers {
    public static void main(String[] args) {
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .install(EventHandler.Feature, cfg -> {
                // Handle tool calls
                cfg.onToolCallStarting(ctx -> {
                    System.out.println("Tool called: " + ctx.getToolName() + " with args " + ctx.getToolArgs());
                });
                // Handle event triggered when the agent completes its execution
                cfg.onAgentCompleted(ctx -> {
                    System.out.println("Agent finished with result: " + ctx.getResult());
                });
            })
            .build();

        AIAgent<String, String> agent1 = AIAgent.builder()
            .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .install(EventHandler.Feature, cfg -> {
                // Handle tool calls
                cfg.onToolCallStarting(ctx -> {
                    System.out.println("Tool called: " + ctx.getToolName() + " with args " + ctx.getToolArgs());
                });
                // Handle event triggered when the agent completes its execution
                cfg.onAgentCompleted(ctx -> {
                    System.out.println("Agent finished with result: " + ctx.getResult());
                });
            })
            .build();
    }
}