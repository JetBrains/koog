import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import kotlin.reflect.KFunction;
import kotlinx.serialization.json.Json;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;
import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

public class ExampleAnnotationBasedTools {
    public class MyToolSet implements ToolSet {
        @Tool
        public String myTool() {
            // Tool implementation
            return "Result";
        }

        @Tool(customName = "customToolName")
        public String anotherTool() {
            // Tool implementation
            return "Result";
        }
    }

    public class MyTools implements ToolSet {
        @Tool
        @LLMDescription(description = "Performs a specific operation and returns the result")
        public String myTool() {
            // Function implementation
            return "Result";
        }
    }

    public class ProcessingTools implements ToolSet {
        @Tool
        @LLMDescription(description = "Processes input data")
        public String processTool(
                @LLMDescription(description = "The input data to process") String input,
                @LLMDescription(description = "Optional configuration parameters") String config
        ) {
            // Function implementation
            return "Processed: " + input + " with config: " + config;
        }
    }

    @LLMDescription(description = "Tools for getting weather information")
    public static class MyFirstToolSet implements ToolSet {
        @Tool
        @LLMDescription(description = "Get the current weather for a location")
        public String getWeather(
                @LLMDescription(description = "The city and state/country") String location
        ) {
            // In a real implementation, you would call a weather API
            return "The weather in " + location + " is sunny and 72°F";
        }
    }

    public static void main(String[] args) {

        MyFirstToolSet weatherTools = new MyFirstToolSet();

        ToolRegistry toolRegistry = ToolRegistry.builder()
                .tools(weatherTools)
                .build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
            .systemPrompt("Provide weather information for a given location.")
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .toolRegistry(toolRegistry)
            .build();

        String result = agent.run("What's the weather like in New York?");
        System.out.println(result);
    }
}