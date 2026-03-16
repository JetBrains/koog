package ai.koog.agents.example.simpleapi;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import me.kpavlov.finchly.TestEnvironment;

import java.util.Scanner;

/**
 * This example demonstrates how to create an agent with tools using the Java API.
 * The agent can toggle and query the state of a Switch via tool calls.
 */
public class BasicSingleRunAgent {

    public static void main(String[] args) {
//        String apiKey = System.getenv("OPENAI_API_KEY");
        String apiKey = TestEnvironment.INSTANCE.get("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
        }

        Switch theSwitch = new Switch();

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(new MultiLLMPromptExecutor(new OpenAILLMClient(apiKey)))
                .llmModel(OpenAIModels.Chat.GPT4oMini)
                .systemPrompt("You're responsible for running a Switch and perform operations on it by request")
                .temperature(0.0)
                .toolRegistry(
                        ToolRegistry.builder()
                                .tools(new SwitchTools(theSwitch))
                                .build()
                )
                .build();

        System.out.println("Chat started");
        agent.run(new Scanner(System.in).nextLine());
    }
}
