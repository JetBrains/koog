import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExampleSpringBoot_RobustAIService {
    private static final Logger logger = LoggerFactory.getLogger(ExampleSpringBoot_RobustAIService.class);
    private final MultiLLMPromptExecutor openAIExecutor;
    private final MultiLLMPromptExecutor anthropicExecutor;
    private final MultiLLMPromptExecutor openRouterExecutor;

    public ExampleSpringBoot_RobustAIService(MultiLLMPromptExecutor openAIExecutor,
                           MultiLLMPromptExecutor anthropicExecutor,
                           MultiLLMPromptExecutor openRouterExecutor) {
        this.openAIExecutor = openAIExecutor;
        this.anthropicExecutor = anthropicExecutor;
        this.openRouterExecutor = openRouterExecutor;
    }

    public String generateWithFallback(String input) {
        Prompt prompt = Prompt.builder("robust")
                .system("You are a helpful AI assistant")
                .user(input)
                .build();

        List<MultiLLMPromptExecutor> executors = new ArrayList<>();
        if (openAIExecutor != null) executors.add(openAIExecutor);
        if (anthropicExecutor != null) executors.add(anthropicExecutor);
        if (openRouterExecutor != null) executors.add(openRouterExecutor);

        for (MultiLLMPromptExecutor executor : executors) {
            try {
                // FAILED: PromptExecutor.execute is suspend-only and requires an explicit model.
                // Example (requires helper):
                // List<Message.Response> result = JavaUtils.executeExecutorBlocking(executor, prompt, SomeModels.Default);
                // return result.get(0).getContent();
                throw new IllegalStateException("Suspend-only API, model required");
            } catch (Exception e) {
                logger.warn("Executor failed, trying next: {}", e.getMessage());
            }
        }
        throw new IllegalStateException("All AI providers failed");
    }
}
