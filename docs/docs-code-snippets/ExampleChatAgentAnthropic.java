import ai.koog.agents.chatMemory.feature.ChatMemory;
import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;

import java.util.Scanner;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleAnthropicExecutor;

public class ExampleChatAgentAnthropic {
    public static void main(String[] args) {
        String sessionId = "my-conversation";

        ToolRegistry toolRegistry = ToolRegistry.builder()
                // register your tools here
                .build();

        try (var executor = simpleAnthropicExecutor(System.getenv("ANTHROPIC_API_KEY"))) {
            AIAgent<String, String> agent = AIAgent.builder()
                    .promptExecutor(executor)
                    .llmModel(AnthropicModels.Sonnet_4_5)
                    .systemPrompt("You are a helpful assistant.")
                    .toolRegistry(toolRegistry)
                    .install(ChatMemory.Feature, config -> {
                        config.windowSize(20); // keep only the last 20 messages
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
