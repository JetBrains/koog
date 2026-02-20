import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.testing.tools.MockExecutor;
import ai.koog.prompt.executor.model.PromptExecutor;

public class ExampleTesting {
    public static void main(String[] args) {

        /* Mock LLM response*/
        // Create a tool registry (empty)
        ToolRegistry toolRegistry = ToolRegistry.builder().build();

        // Create a mock LLM executor
        PromptExecutor mockLLMApi = MockExecutor.builder()
            .toolRegistry(toolRegistry)
            .mockLLMAnswer("Hello!").onRequestContains("Hello")
            .mockLLMAnswer("I don't know how to answer that.").asDefaultResponse()
            .build();

        /* Simulate different LLM responses based on input */

        PromptExecutor promptExecutor = MockExecutor.builder()
            .mockLLMAnswer("Response A").onRequestContains("topic A")
            .mockLLMAnswer("Response B").onRequestContains("topic B")
            .mockLLMAnswer("Exact response").onRequestEquals("exact question")
            .mockLLMAnswer("Conditional response").onCondition(s -> s.contains("keyword") && s.length() > 10)
            .build();
    }
}