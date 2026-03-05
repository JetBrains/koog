import ai.koog.agents.chatMemory.feature.ChatMemory;
import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;

import java.util.Scanner;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

public class ExampleChatAgentOpenAI {
    public static void main(String[] args) {
        String sessionId = "my-conversation";

        ToolRegistry toolRegistry = ToolRegistry.builder()
                // register your tools here
                .build();

        try (var executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))) {
            AIAgent<String, String> agent = AIAgent.builder()
                    .promptExecutor(executor)
                    .llmModel(OpenAIModels.Chat.GPT5_2)
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
