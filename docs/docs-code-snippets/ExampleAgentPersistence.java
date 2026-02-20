import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.RollbackStrategy;
import ai.koog.agents.snapshot.feature.Persistence;
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider;
import ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt; // Kotlin top-level helper for simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels;

public class ExampleAgentPersistence {
    public static void main(String[] args) {
        /* Installation */
        AIAgent<String, String> agent = AIAgent.<String, String>builder()
            .promptExecutor(SimplePromptExecutorsKt.simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .install(Persistence.Feature, cfg -> {
                cfg.setStorage(new InMemoryPersistenceStorageProvider());
                cfg.setEnableAutomaticPersistence(true);
                cfg.setRollbackStrategy(RollbackStrategy.MessageHistoryOnly);
            })
            .build();

        // Use the agent...
        String result = agent.run("Hello! How can you help me?");
        System.out.println(result);

        /* Storage provider */
        AIAgent<String, String> agent2 = AIAgent.<String, String>builder()
            .promptExecutor(SimplePromptExecutorsKt.simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .install(Persistence.Feature, cfg -> {
                cfg.setStorage(new InMemoryPersistenceStorageProvider());
            })
            .build();

        /* Continuous persistence */
        AIAgent<String, String> agent3 = AIAgent.<String, String>builder()
            .promptExecutor(SimplePromptExecutorsKt.simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .install(Persistence.Feature, cfg -> {
                cfg.setEnableAutomaticPersistence(true);
            })
            .build();

        /* Rollback strategy */
        AIAgent<String, String> agent4 = AIAgent.<String, String>builder()
            .promptExecutor(SimplePromptExecutorsKt.simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .install(Persistence.Feature, cfg -> {
                cfg.setRollbackStrategy(RollbackStrategy.Default);
            })
            .build();

        AIAgent<String, String> agen5 = AIAgent.<String, String>builder()
            .promptExecutor(SimplePromptExecutorsKt.simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .install(Persistence.Feature, cfg -> {
                cfg.setRollbackStrategy(RollbackStrategy.MessageHistoryOnly);
            })
            .build();

    }
}
