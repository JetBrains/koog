package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.ToolCalls;
import ai.koog.agents.core.tools.Tool;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaInteropUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import kotlinx.serialization.Serializable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaContextApiIntegrationTest extends KoogJavaTestBase {

    @Serializable
    public static class CalculationResult {
        private final int result;
        private final String operation;

        public CalculationResult(int result, String operation) {
            this.result = result;
            this.operation = operation;
        }

        public int getResult() {
            return result;
        }

        public String getOperation() {
            return operation;
        }
    }

    @Serializable
    public static class PersonInfo {
        private final String name;
        private final int age;
        private final List<String> hobbies;

        public PersonInfo(String name, int age, List<String> hobbies) {
            this.name = name;
            this.age = age;
            this.hobbies = hobbies;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public List<String> getHobbies() {
            return hobbies;
        }
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_RequestLLMStructuredSimple(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that provides structured responses.")
                .functionalStrategy((context, input) -> {
                    CalculationResult calc = JavaInteropUtils.requestLLMStructuredBlocking(
                        context,
                        "Calculate 15 + 27 and return the result in the specified format",
                        CalculationResult.class
                    );
                    return "Result: " + calc.getResult() + ", Operation: " + calc.getOperation();
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Calculate 15 + 27");

        assertNotNull(result);
        assertTrue(result.contains("42"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_RequestLLMStructuredComplex(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant that provides structured responses about people.")
                .functionalStrategy((context, input) -> {
                    PersonInfo person = JavaInteropUtils.requestLLMStructuredBlocking(
                        context,
                        "Create a person profile with name 'Alice', age 30, and hobbies: reading, coding, hiking",
                        PersonInfo.class
                    );
                    return "Name: " + person.getName() + ", Age: " + person.getAge() +
                        ", Hobbies: " + person.getHobbies().size();
                })
        );

        String result = runBlocking(continuation -> agent.run("Create person profile", null, continuation));

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("Alice"));
        assertTrue(result.contains("30"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_LLMWriteSession(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) ->
                    JavaInteropUtils.llmWriteSession(context, writeSession -> {
                        writeSession.appendPrompt(prompt -> {
                            prompt.user("First question: What is 2+2?");
                            return null;
                        });

                        writeSession.appendPrompt(prompt -> {
                            prompt.user("Second question: What is 3+3?");
                            return null;
                        });

                        Message.Response response2 = writeSession.requestLLM();

                        if (response2 instanceof Message.Assistant) {
                            return JavaInteropUtils.getAssistantContent((Message.Assistant) response2);
                        }
                        return "Unexpected response type";
                    })
                )
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Test");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("6") || result.contains("six"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_LLMReadSession(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    Message.Response response = JavaInteropUtils.requestLLM(context, "What is 5+5?", true);

                    return JavaInteropUtils.llmReadSession(context, readSession -> {
                        var messages = readSession.getPrompt().getMessages();
                        int historySize = messages.size();

                        if (response instanceof Message.Assistant) {
                            return "History size: " + historySize + ", Answer: " +
                                JavaInteropUtils.getAssistantContent((Message.Assistant) response);
                        }
                        return "Unexpected response type";
                    });
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Test");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("History size:"));
        assertTrue(result.contains("10") || result.contains("ten"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_SubtaskSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        List<Tool<?, ?>> tools = List.of(calculator.getAddTool());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a coordinator that delegates calculations.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    String subtaskResult = context.subtask("Calculate the sum of 10 and 20 using the add tool")
                        .withInput(input)
                        .withOutput(String.class)
                        .withTools(tools)
                        .useLLM(model)
                        .runMode(ToolCalls.SEQUENTIAL)
                        .run();

                    return "Subtask completed: " + subtaskResult;
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Perform calculation");

        assertNotNull(result);
        assertTrue(result.contains("Subtask completed"));
        assertTrue(result.contains("30") || result.contains("thirty"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_SubtaskParallel(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        List<Tool<?, ?>> tools = List.of(calculator.getAddTool(), calculator.getMultiplyTool());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a coordinator that delegates calculations.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    String subtaskResult = context.subtask("Calculate 5 + 3 and 4 * 6 using available tools")
                        .withInput(input)
                        .withOutput(String.class)
                        .withTools(tools)
                        .useLLM(model)
                        .runMode(ToolCalls.PARALLEL)
                        .run();

                    return "Parallel subtask result: " + subtaskResult;
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Perform parallel calculations");

        assertNotNull(result);
        assertTrue(result.contains("Parallel subtask result"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_SubtaskSingleRunSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        List<Tool<?, ?>> tools = List.of(calculator.getAddTool());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a coordinator.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    String subtaskResult = context.subtask("Add 7 and 8")
                        .withInput(input)
                        .withOutput(String.class)
                        .withTools(tools)
                        .useLLM(model)
                        .runMode(ToolCalls.SINGLE_RUN_SEQUENTIAL)
                        .run();

                    return "Single-run result: " + subtaskResult;
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Calculate");

        assertNotNull(result);
        assertTrue(result.contains("Single-run result"));
        assertTrue(result.contains("15") || result.contains("fifteen"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ExecuteMultipleToolsParallel(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a calculator. Use add and multiply tools.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    Message.Response response = JavaInteropUtils.requestLLM(
                        context,
                        "Calculate 5+3 and 4*6. You can use multiple tools in parallel.",
                        true
                    );

                    if (response instanceof Message.Tool.Call) {
                        List<Message.Tool.Call> calls = new ArrayList<>();
                        calls.add((Message.Tool.Call) response);

                        if (!calls.isEmpty()) {
                            var results = JavaInteropUtils.executeMultipleTools(context, calls, true);
                            var responses = JavaInteropUtils.sendMultipleToolResults(context, results);
                            if (!responses.isEmpty() && responses.get(0) instanceof Message.Assistant) {
                                return JavaInteropUtils.getAssistantContent((Message.Assistant) responses.get(0));
                            }
                        }
                    }

                    return "Parallel execution completed";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Calculate");

        assertNotNull(result);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ExecuteSingleTool(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You coordinate tool execution.")
                .toolRegistry(JavaInteropUtils.createToolRegistry(calculator))
                .functionalStrategy((context, input) -> {
                    Message.Response response = JavaInteropUtils.requestLLM(
                        context,
                        "Use the add tool to calculate 10 + 5",
                        true
                    );

                    if (response instanceof Message.Tool.Call) {
                        Message.Tool.Call toolCall = (Message.Tool.Call) response;
                        var toolResult = JavaInteropUtils.executeTool(context, toolCall);
                        var finalResponse = JavaInteropUtils.sendToolResult(context, toolResult);

                        if (finalResponse instanceof Message.Assistant) {
                            return JavaInteropUtils.getAssistantContent((Message.Assistant) finalResponse);
                        }
                    }

                    return "Tool execution completed";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Test");

        assertNotNull(result);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GetHistory(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = JavaInteropUtils.buildFunctionalAgent(
            JavaInteropUtils.createAgentBuilder()
                .promptExecutor(createExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant.")
                .functionalStrategy((context, input) -> {
                    JavaInteropUtils.requestLLM(context, "First question: What is 2+2?", true);
                    JavaInteropUtils.requestLLM(context, "Second question: What is 3*3?", true);

                    var history = JavaInteropUtils.getHistory(context);
                    int historySize = history.size();

                    return "History contains " + historySize + " messages";
                })
        );

        String result = JavaInteropUtils.runAgentBlocking(agent, "Test");

        assertNotNull(result);
        assertTrue(result.contains("History contains"));
        assertTrue(result.matches(".*History contains \\d+ messages.*"));
    }
}
