import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.prompt.executor.ollama.client.OllamaModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;

public class ExampleClassBasedTools {
    // Java equivalent: implement the tool as a Java method and register it via ToolRegistry.builder().
    // This is the recommended Java interop path instead of subclassing the Kotlin Tool base class.
    public final class CalculatorTool {
        private CalculatorTool() {}

        @Tool(customName = "calculator")
        @LLMDescription(description = "A simple calculator that can add two digits (0-9).")
        public static int calculator(
                @LLMDescription(description = "The first digit to add (0-9)") int digit1,
                @LLMDescription(description = "The second digit to add (0-9)") int digit2
        ) {
            if (digit1 < 0 || digit1 > 9) throw new IllegalArgumentException("digit1 must be a single digit (0-9)");
            if (digit2 < 0 || digit2 > 9) throw new IllegalArgumentException("digit2 must be a single digit (0-9)");
            return digit1 + digit2;
        }

        public static ToolRegistry registry() throws NoSuchMethodException {
            return ToolRegistry.builder()
                    .tool(CalculatorTool.class.getMethod("calculator", int.class, int.class))
                    .build();
        }
    }
    // Note: Subclassing the Kotlin Tool<TArgs, TResult> and overriding a suspend execute(...) from Java is not supported.
    // The Java interop uses reflection-based registration of Java methods as tools.

    // Java equivalent: implement the tool as a Java method and register it via ToolRegistry.builder().
    // This is the recommended Java interop path instead of subclassing the Kotlin Tool base class.
    public final class CastToDoubleTool {
        private CastToDoubleTool() {}

        @Tool(customName = "cast_to_double")
        @LLMDescription(description = "casts the passed expression to double or returns 0.0 if the expression is not castable")
        public static String castToDouble(
                @LLMDescription(description = "An expression to case to double") String expression,
                @LLMDescription(description = "A comment on how to process the expression") String comment
        ) {
            double value;
            try {
                value = Double.parseDouble(expression);
            } catch (Exception e) {
                value = 0.0;
            }
            return "Result: " + value + ", the comment was: " + comment;
        }

        public static ToolRegistry registry() throws NoSuchMethodException {
            return ToolRegistry.builder()
                    .tool(CastToDoubleTool.class.getMethod("castToDouble", String.class, String.class))
                    .build();
        }
    }
    // Note: Subclassing the Kotlin Tool<TArgs, TResult> and overriding a suspend execute(...) from Java is not supported.
    // The Java interop uses reflection-based registration of Java methods as tools.

    // Java equivalent: return Markdown text directly to the LLM from a Java method and register it as a tool.
    // This avoids needing a custom serializable Result type (which would require Kotlin serialization support).
    public final class EditFile {
        private EditFile() {}

        @Tool(customName = "edit_file")
        @LLMDescription(description = "Edits the given file")
        public static String editFile(
                String path,
                String original,
                String replacement
        ) {
            // TODO: Implement file edit logic; below is a placeholder illustrating Markdown output
            boolean success = false;
            if (success) {
                return "**Successfully** edited file (patch applied)";
            } else {
                return "File was **not** modified (patch application failed: reason)";
            }
        }

        public static ToolRegistry registry() throws NoSuchMethodException {
            return ToolRegistry.builder()
                    .tool(EditFile.class.getMethod("editFile", String.class, String.class, String.class))
                    .build();
        }
    }
    // Note: If you need a structured custom Result object from Java, you must expose a Kotlin @Serializable type
    // or another serializer-aware type. Returning String works out-of-the-box with Koog's Java interop.

    public static void main(String[] args) throws NoSuchMethodException {

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
            .systemPrompt("Provide weather information for a given location.")
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .toolRegistry(CastToDoubleTool.registry())
            .build();

        String result = agent.run("What's the weather like in New York?");
        System.out.println(result);
    }
}