package ai.koog.agents.example.chat;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutors.simpleOllamaAIExecutor;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.executor.ollama.client.OllamaModels.Meta;
import ai.koog.prompt.message.Message;
import java.util.Scanner;
import kotlin.Unit;

/**
 * Demonstrates how to build a functional chat agent in Java using Koog APIs.
 *
 * <p>Usage: {@code ./gradlew runExampleFunctionalAgentChatJava}
 */
public class FunctionalAgentChat {

    public static void main(String[] args) {
        try (
            PromptExecutor executor = simpleOllamaAIExecutor("http://localhost:11434");
            Scanner scanner = new Scanner(System.in);
        ) {
            AIAgent<String, Unit> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(Meta.LLAMA_3_2)
                .systemPrompt("You're a simple chat agent")
                .functionalStrategy("chat", (AIAgentFunctionalContext context, String firstUserInput) -> {
                    String userInput = firstUserInput;
                    while (!"/bye".equals(userInput)) {
                        Message.Response response = context.requestLLM(userInput);
                        if (response instanceof Message.Assistant assistantResponse) {
                            System.out.println(assistantResponse.getContent());
                        }
                        userInput = scanner.nextLine();
                    }
                    return Unit.INSTANCE;
                })
                .build();

            System.out.println("Simple chat agent started");
            System.out.println("Use /bye to quit");
            System.out.println("Enter your message:");
            String input = scanner.nextLine();
            agent.run(input);
        } catch (Exception e) {
            System.err.println("Functional chat example failed: " + e.getMessage());
            System.err.println("Check that Ollama is running at http://localhost:11434 and model llama3.2 is available.");
            throw new RuntimeException("Unable to run FunctionalAgentChat example", e);
        }
    }
}
