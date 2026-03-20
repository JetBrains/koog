package ai.koog.agents.example.features.opentelemetry.langfuse;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.example.ApiKeyService;
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute;
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;

import java.util.List;
import java.util.UUID;

public class LangfuseExample {

    public static void main(String[] args) {
        var promptExecutor = PromptExecutor.builder()
            .openAI(ApiKeyService.getOpenAIApiKey())
            .build();

        var sessionId = UUID.randomUUID().toString();

        var agent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .systemPrompt("You are a helpful assistant.")
            .llmModel(OpenAIModels.Chat.GPT4o)
            .install(OpenTelemetry.Feature, config ->
                config.addLangfuseExporter(
                    null, null, null, null,
                    List.of(
                        new CustomAttribute("langfuse.session.id", sessionId),
                        new CustomAttribute("langfuse.trace.tags", List.of("chat", "kotlin", "production"))
                    )
                ))
            .build();

        System.out.println("Running agent with Langfuse tracing");

        var result = agent.run("How to setup Langfuse integration in Koog agent?");
        System.out.println("Result: " + result);
    }
}
