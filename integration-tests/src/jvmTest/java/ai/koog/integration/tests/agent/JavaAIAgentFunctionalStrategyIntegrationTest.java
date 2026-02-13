package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaUtils;
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
            return JavaUtils.getAssistantContent((Message.Assistant) response);
        }
        return defaultValue;
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_SimpleFunctionalStrategyWithRetry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        // Test simple functional strategy with retry logic
        AIAgent<String, String> agent = JavaUtils.buildFunctionalAgent(
            JavaUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    for (int i = 0; i < 3; i++) {
                        String result = getAssistantContentOrDefault(
                            JavaUtils.requestLLM(context, input, true),
                            ""
                        );
                        if (!result.isEmpty()) {
                            return result;
                        }
                    }
                    return "Failed after retries";
                })
        );

        String result = JavaUtils.runAgentBlocking(agent, "Say hello");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("hello"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_MultiStepFunctionalStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaUtils.buildFunctionalAgent(
            JavaUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    Message.Response response1 = JavaUtils.requestLLM(context, "First step: " + input, true);
                    String step1Result = getAssistantContentOrDefault(response1, "");

                    Message.Response response2 = JavaUtils.requestLLM(
                        context,
                        "Second step, previous result was: " + step1Result,
                        true
                    );

                    return getAssistantContentOrDefault(response2, "Unexpected response type");
                })
        );

        String result = JavaUtils.runAgentBlocking(agent, "Count to 3");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_FunctionalStrategyWithManualToolHandling(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaUtils.CalculatorTools calculator = new JavaUtils.CalculatorTools();
        ToolRegistry toolRegistry = JavaUtils.createToolRegistry(calculator);

        AIAgent<String, String> agent = JavaUtils.buildFunctionalAgent(
            JavaUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a calculator. Use the add tool to perform calculations.")
                .toolRegistry(toolRegistry)
                .functionalStrategy((context, input) -> {
                    Message.Response currentResponse = JavaUtils.requestLLM(
                        context,
                        "Calculate: " + input + ". You MUST use the add tool.",
                        true
                    );

                    int maxIterations = 5;
                    for (int i = 0; i < maxIterations && currentResponse instanceof Message.Tool.Call; i++) {
                        Message.Tool.Call toolCall = (Message.Tool.Call) currentResponse;
                        ReceivedToolResult toolResult = JavaUtils.executeTool(context, toolCall);
                        currentResponse = JavaUtils.sendToolResult(context, toolResult);
                    }

                    if (currentResponse instanceof Message.Assistant) {
                        return JavaUtils.getAssistantContent((Message.Assistant) currentResponse);
                    } else if (currentResponse instanceof Message.Tool.Call) {
                        return "Max iterations reached, last tool: " + JavaUtils.getToolName((Message.Tool.Call) currentResponse);
                    }
                    return "Unexpected response type";
                })
        );

        String result = JavaUtils.runAgentBlocking(agent, "10 + 5");

        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_Subtask(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        JavaUtils.CalculatorTools calculator = new JavaUtils.CalculatorTools();

        List<Tool<?, ?>> calculatorTools = List.of(
            calculator.getAddTool(),
            calculator.getMultiplyTool()
        );

        AIAgent<String, String> agent = JavaUtils.buildFunctionalAgent(
            JavaUtils.createAgentBuilder()
                .promptExecutor(executor)
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that coordinates calculations.")
                .toolRegistry(JavaUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    String subtaskResult = JavaUtils.runSubtask(
                        context,
                        "Calculate: " + input,
                        input,
                        String.class,
                        calculatorTools,
                        model
                    );

                    return "Calculation result: " + subtaskResult;
                })
        );

        String result = JavaUtils.runAgentBlocking(agent, "What is 5 + 3?");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_CustomStrategyWithValidation(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaUtils.buildFunctionalAgent(
            JavaUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that generates JSON.")
                .functionalStrategy((context, input) -> {
                    Message.Response response = JavaUtils.requestLLM(
                        context,
                        "Generate a JSON object with 'status' field set to 'success'",
                        true
                    );

                    String content = getAssistantContentOrDefault(response, "Unexpected response type");
                    if (content.contains("status") && content.contains("success")) {
                        return content;
                    }
                    return "Validation failed: response doesn't contain expected fields";
                })
        );

        String result = JavaUtils.runAgentBlocking(agent, "Generate status JSON");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
