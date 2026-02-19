import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ExampleSpringBoot_AIService {
    private final MultiLLMPromptExecutor openAIExecutor;
    private final MultiLLMPromptExecutor anthropicExecutor;

    public ExampleSpringBoot_AIService(MultiLLMPromptExecutor openAIExecutor, MultiLLMPromptExecutor anthropicExecutor) {
        this.openAIExecutor = openAIExecutor;
        this.anthropicExecutor = anthropicExecutor;
    }

    public String generateResponse(String input) {
        Prompt prompt = Prompt.builder("ai-service")
            .system("You are a helpful AI assistant")
            .user(input)
            .build();

        if (openAIExecutor != null) {
            // FAILED: suspend-only API; requires model and runBlocking helper (see JavaUtils in tests)
            return "";
        } else if (anthropicExecutor != null) {
            // FAILED: suspend-only API; requires model and runBlocking helper (see JavaUtils in tests)
            return "";
        } else {
            throw new IllegalStateException("No LLM provider configured");
        }
    }

    // Solution to the "No qualifying bean of type 'MultiLLMPromptExecutor' available" issue
    public class MyService {
        private final MultiLLMPromptExecutor openAIExecutor;
        private final MultiLLMPromptExecutor anthropicExecutor;

        public MyService(@Qualifier("openAIExecutor") MultiLLMPromptExecutor openAIExecutor,
                         @Qualifier("anthropicExecutor") MultiLLMPromptExecutor anthropicExecutor) {
            this.openAIExecutor = openAIExecutor;
            this.anthropicExecutor = anthropicExecutor;
        }
        // ...
    }
}
