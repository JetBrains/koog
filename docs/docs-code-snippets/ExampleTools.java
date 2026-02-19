import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.ext.tool.SayToUser;
import ai.koog.prompt.executor.ollama.client.OllamaModels;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;

public class ExampleTools {
    public static class FirstToolSet implements ToolSet {
        @Tool
        public String myTool() {
            // Tool implementation
            return "First result";
        }
    }

    public static class SecondToolSet implements ToolSet {
        @Tool
        public String myTool() {
            // Tool implementation
            return "Second result";
        }
    }

    public static void main(String[] args) {

        FirstToolSet firstSampleTool = new FirstToolSet();
        SecondToolSet secondSampleTool = new SecondToolSet();

        ToolRegistry firstToolRegistry = ToolRegistry.builder()
            .tools(firstSampleTool)
            .build();

        ToolRegistry secondToolRegistry = ToolRegistry.builder()
            .tools(secondSampleTool)
            .build();

        ToolRegistry newRegistry = firstToolRegistry.plus(secondToolRegistry);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")))
            .systemPrompt("You are a helpful assistant with strong mathematical skills.")
            .llmModel(OpenAIModels.Chat.GPT4o)
            .toolRegistry(ToolRegistry.builder()
                    .tools(secondSampleTool)
                    .build()
            )
            .build();

        String result = agent.run("What's the weather like in New York?");
        System.out.println(result);
    }
}