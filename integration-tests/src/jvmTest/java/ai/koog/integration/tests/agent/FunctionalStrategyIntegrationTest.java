package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.AIAgentBuilder;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaUtils;
import ai.koog.integration.tests.utils.NumberTools;
import ai.koog.prompt.Prompt;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.MessagePart;
import ai.koog.serialization.kotlinx.KotlinxSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class FunctionalStrategyIntegrationTest extends KoogJavaTestBase {
    private AIAgentBuilder javaBuilder(LLModel model) {
        return AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .agentConfig(
                AIAgentConfig.builder()
                    .model(model)
                    .serializer(new KotlinxSerializer())
                    .build()
            );
    }

    private String getAssistantContentOrDefault(Message.Assistant response, String defaultValue) {
        String text = response.getParts().stream()
            .filter(p -> p instanceof MessagePart.Text)
            .map(p -> ((MessagePart.Text) p).getText())
            .collect(Collectors.joining("\n"));
        return text.isEmpty() ? defaultValue : text;
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_SimpleFunctionalStrategyWithRetry(LLModel model) {
        JavaUtils.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = javaBuilder(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                for (int i = 0; i < 3; i++) {
                    String result = getAssistantContentOrDefault(
                        context.requestLLM(input),
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_MultiStepFunctionalStrategy(LLModel model) {
        JavaUtils.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = javaBuilder(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Assistant response1 = context.requestLLM("First step: " + input);
                String step1Result = getAssistantContentOrDefault(response1, "");

                Message.Assistant response2 = context.requestLLM(
                    "Second step, previous result was: " + step1Result
                );

                return getAssistantContentOrDefault(response2, "Unexpected response type");
            })
            .build();

        String result = agent.run("Count to 3");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_FunctionalStrategyWithManualToolHandling(LLModel model) {
        JavaUtils.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculator).build();

        AIAgent<String, String> agent = javaBuilder(model)
            .systemPrompt("You are a calculator. You MUST use the add tool to perform calculations. DO NOT answer without calling tools.")
            .toolRegistry(toolRegistry)
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Assistant currentResponse = context.requestLLM(
                    "Calculate: " + input + ". You MUST use the add tool."
                );

                int maxIterations = 5;
                for (int i = 0; i < maxIterations; i++) {
                    Optional<MessagePart.Tool.Call> maybeToolCall = currentResponse.getParts().stream()
                        .filter(p -> p instanceof MessagePart.Tool.Call)
                        .map(p -> (MessagePart.Tool.Call) p)
                        .findFirst();
                    if (maybeToolCall.isEmpty()) {
                        break;
                    }
                    ReceivedToolResult toolResult = context.executeTool(maybeToolCall.get());
                    currentResponse = context.sendToolResult(toolResult);
                }

                Optional<MessagePart.Tool.Call> remaining = currentResponse.getParts().stream()
                    .filter(p -> p instanceof MessagePart.Tool.Call)
                    .map(p -> (MessagePart.Tool.Call) p)
                    .findFirst();
                if (remaining.isPresent()) {
                    return "Max iterations reached, last tool: " + remaining.get().getTool();
                }
                return getAssistantContentOrDefault(currentResponse, "");
            })
            .build();

        String result = agent.run("10 + 5");

        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_Subtask(LLModel model) {
        JavaUtils.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        NumberTools calculator = new NumberTools();

        List<Tool<?, ?>> calculatorTools = List.of(
            calculator.getTool("add"),
            calculator.getTool("multiply")
        );

        AIAgent<String, String> agent = AIAgent.builder()
            .agentConfig(
                AIAgentConfig.builder()
                    .model(model)
                    .prompt(
                        Prompt.builder("task")
                            .system("You are a helpful assistant that coordinates calculations.")
                            .build()
                    )
                    .serializer(new KotlinxSerializer())
                    .strategyExecutorService(Executors.newFixedThreadPool(4))
                    .llmRequestExecutorService(Executors.newFixedThreadPool(4))
                    .build()
            )
            .promptExecutor(executor)
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                String subtaskResult = context.subtask("Calculate: " + input)
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_CustomStrategyWithValidation(LLModel model) {
        JavaUtils.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = javaBuilder(model)
            .systemPrompt("You are a helpful assistant that generates JSON.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Assistant response = context.requestLLM(
                    "Generate a JSON object with 'status' field set to 'success'"
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
