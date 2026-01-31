package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaInteropUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AIAgent with custom functional strategies.
 */
public class JavaAIAgentFunctionalStrategyIntegrationTest extends KoogJavaTestBase {

    private String getAssistantContentOrDefault(Message.Response response, String defaultValue) {
        if (response instanceof Message.Assistant) {
            return response.getContent();
        }
        return defaultValue;
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_SimpleFunctionalStrategyWithRetry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        // Test simple functional strategy with retry logic
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                for (int i = 0; i < 3; i++) {
                    String result = getAssistantContentOrDefault(
                        context.requestLLM(input, true),
                        ""
                    );
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
                return "Failed after retries";
            })
            .build();

        String result = agent.run("Say hello");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("hello"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_MultiStepFunctionalStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Response response1 = context.requestLLM("First step: " + input, true);
                String step1Result = getAssistantContentOrDefault(response1, "");

                Message.Response response2 = context.requestLLM(
                    "Second step, previous result was: " + step1Result,
                    true
                );

                return getAssistantContentOrDefault(response2, "Unexpected response type");
            })
            .build();

        String result = agent.run("Count to 3");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_FunctionalStrategyWithManualToolHandling(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        CalculatorTools calculator = new CalculatorTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculator).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. Use the add tool to perform calculations.")
            .toolRegistry(toolRegistry)
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Response currentResponse = context.requestLLM(
                    "Calculate: " + input + ". You MUST use the add tool.",
                    true
                );

                int maxIterations = 5;
                for (int i = 0; i < maxIterations && currentResponse instanceof Message.Tool.Call; i++) {
                    Message.Tool.Call toolCall = (Message.Tool.Call) currentResponse;
                    ReceivedToolResult toolResult = context.executeTool(toolCall);
                    currentResponse = context.sendToolResult(toolResult);
                }

                if (currentResponse instanceof Message.Assistant) {
                    return currentResponse.getContent();
                } else if (currentResponse instanceof Message.Tool.Call) {
                    return "Max iterations reached, last tool: " + ((Message.Tool.Call) currentResponse).getTool();
                }
                return "Unexpected response type";
            })
            .build();

        String result = agent.run("10 + 5");

        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_Subtask(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        CalculatorTools calculator = new CalculatorTools();

        List<Tool<?, ?>> calculatorTools = List.of(
            calculator.getTool("add"),
            calculator.getTool("multiply")
        );

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .systemPrompt("You are a helpful assistant that coordinates calculations.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                String subtaskResult = context.subtask("Calculate: " + input)
                    .withInput(input)
                    .withOutput(String.class)
                    .withTools(calculatorTools)
                    .useLLM(model)
                    .run();

                return "Calculation result: " + subtaskResult;
            })
            .build();

        String result = agent.run("What is 5 + 3?");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_CustomStrategyWithValidation(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant that generates JSON.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Response response = context.requestLLM(
                    "Generate a JSON object with 'status' field set to 'success'",
                    true
                );

                String content = getAssistantContentOrDefault(response, "Unexpected response type");
                if (content.contains("status") && content.contains("success")) {
                    return content;
                }
                return "Validation failed: response doesn't contain expected fields";
            })
            .build();

        String result = agent.run("Generate status JSON");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
