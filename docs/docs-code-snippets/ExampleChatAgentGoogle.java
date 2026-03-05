import ai.koog.agents.chatMemory.feature.ChatMemory;
import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.google.GoogleModels;

import java.util.Scanner;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleGoogleAIExecutor;

public class ExampleChatAgentGoogle {
    public static void main(String[] args) {
        String sessionId = "my-conversation";

        ToolRegistry toolRegistry = ToolRegistry.builder()
                // register your tools here
                .build();

        try (var executor = simpleGoogleAIExecutor(System.getenv("GOOGLE_API_KEY"))) {
            AIAgent<String, String> agent = AIAgent.builder()
                    .promptExecutor(executor)
                    .llmModel(GoogleModels.Gemini2_5Pro)
                    .systemPrompt("You are a helpful assistant.")
                    .toolRegistry(toolRegistry)
                    .install(ChatMemory.Feature, config -> {
                        config.windowSize(20);
                    })
                    .build();

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("You: ");
                String input = scanner.nextLine().trim();
                if (input.equals("/bye")) break;
                if (input.isEmpty()) continue;

                String reply = agent.run(input, sessionId);
                System.out.println("Assistant: " + reply + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
