import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

public class ExamplePromptsIndex {
    public static void main(String[] args) {
        Prompt myPrompt = Prompt.builder("hello-koog")
            .system("You are a helpful assistant.")
            .user("What is Koog?")
            .build();

        System.out.println("Prompt ID: " + myPrompt.getId());
        System.out.println("Messages: " + myPrompt.getMessages().size());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")))
            .systemPrompt("You are a helpful assistant. Answer user questions concisely.")
            .llmModel(OpenAIModels.Chat.GPT4o)
            .build();

        var result = agent.run("What is Koog?");
        System.out.println(result);
    }
}
