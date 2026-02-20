import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.RollbackStrategy;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.snapshot.feature.Persistence;
import ai.koog.agents.snapshot.feature.Reverts;
import ai.koog.agents.snapshot.feature.RollbackToolRegistry;
import ai.koog.agents.snapshot.feature.RollbackToolSet;
import ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt; // Kotlin top-level helper for simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels;

public class ExampleAgentPersistence_ToolSideEffects {

    public static class UserTools implements ToolSet {
        @Tool
        public void createUser(String name) {
            System.out.println(name + " created!");
        }
    }

    public static class UserRollbackTools implements RollbackToolSet {
        @Reverts(toolName = "createUser", toolSet = UserTools.class)
        public void removeUser(String name) {  // Must be public!
            System.out.println(name + " removed!");
        }
    }

    public static void main(String[] args) {
        // FAILED: The provided tool set does not contain a rollback tool for each tool in the provided rollback tool set. Missing tools: createUser
        // Build the registry by pairing a ToolSet with its RollbackToolSet
        RollbackToolRegistry registry = RollbackToolRegistry.builder()
            .registerRollbacks(new UserTools(), new UserRollbackTools())
            .build();

        // Configure the agent Persistence feature and set the registry
        AIAgent<String, String> agent = AIAgent.<String, String>builder()
            .promptExecutor(SimplePromptExecutorsKt.simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .install(Persistence.Feature, cfg -> {
                cfg.setEnableAutomaticPersistence(true);
                cfg.setRollbackStrategy(RollbackStrategy.MessageHistoryOnly);
                cfg.setRollbackToolRegistry(registry);
            })
            .build();
    }
}
