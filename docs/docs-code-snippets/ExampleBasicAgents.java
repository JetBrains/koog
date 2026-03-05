import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;

import java.util.Scanner;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

public class ExampleBasicAgents {

    static class UserConversationTools implements ToolSet {
        @Tool
        @LLMDescription(description = "Ask the user a question by sending it to stdout and return the answer from stdin")
        public String askUser(
            @LLMDescription(description = "Question from the agent")
            String question
        ) {
            System.out.println(question);
            Scanner scanner = new Scanner(System.in);
            return scanner.nextLine();
        }
    }

    public static void main(String[] args) {

        UserConversationTools askUser = new UserConversationTools();

        ToolRegistry toolRegistry = ToolRegistry.builder()
                .tools(askUser)
                .build();

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")))
                .systemPrompt("You are a helpful assistant. Answer user questions concisely.")
                .llmModel(OpenAIModels.Chat.GPT4o)
                .temperature(0.7)
                .toolRegistry(toolRegistry)
                .maxIterations(100)
                .install(EventHandler.Feature, config -> {
                    config.onToolCallStarting(eventContext -> {
                        System.out.println("Tool called: " + eventContext.getToolName() +
                                " with args " + eventContext.getToolArgs());
                    });
                })
                .build();
    }
}
