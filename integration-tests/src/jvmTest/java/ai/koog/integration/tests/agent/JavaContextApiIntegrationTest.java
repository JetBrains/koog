package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.ToolCalls;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
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

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant that provides structured responses.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                CalculationResult calc = JavaInteropUtils.requestLLMStructuredBlocking(
                    context,
                    "Calculate 15 + 27 and return the result in the specified format",
                    CalculationResult.class
                );
                return "Result: " + calc.getResult() + ", Operation: " + calc.getOperation();
            })
            .build();

        String result = agent.run("Calculate 15 + 27");

        assertNotNull(result);
        assertTrue(result.contains("42"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_RequestLLMStructuredComplex(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant that provides structured responses about people.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                PersonInfo person = JavaInteropUtils.requestLLMStructuredBlocking(
                    context,
                    "Create a person profile with name 'Alice', age 30, and hobbies: reading, coding, hiking",
                    PersonInfo.class
                );
                return "Name: " + person.getName() + ", Age: " + person.getAge() +
                    ", Hobbies: " + person.getHobbies().size();
            })
            .build();

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

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) ->
                context.llm().writeSession(session -> {
                    session.appendPrompt(prompt -> {
                        prompt.user("First question: What is 2+2?");
                        return null;
                    });

                    session.appendPrompt(prompt -> {
                        prompt.user("Second question: What is 3+3?");
                        return null;
                    });

                    Message.Response response2 = session.requestLLM();

                    if (response2 instanceof Message.Assistant) {
                        return response2.getContent();
                    }
                    return "Unexpected response type";
                })
            )
            .build();

        String result = agent.run("Test");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("6") || result.contains("six"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_LLMReadSession(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Response response = context.requestLLM("What is 5+5?", true);

                return context.llm().readSession(session -> {
                    List<Message> messages = session.getPrompt().getMessages();
                    int historySize = messages.size();

                    if (response instanceof Message.Assistant) {
                        return "History size: " + historySize + ", Answer: " +
                            response.getContent();
                    }
                    return "Unexpected response type";
                });
            })
            .build();

        String result = agent.run("Test");

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

        CalculatorTools calculator = new CalculatorTools();
        List<Tool<?, ?>> tools = List.of(calculator.getTool("add"));

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a coordinator that delegates calculations.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                String subtaskResult = context.subtask("Calculate the sum of 10 and 20 using the add tool")
                    .withInput(input)
                    .withOutput(String.class)
                    .withTools(tools)
                    .useLLM(model)
                    .runMode(ToolCalls.SEQUENTIAL)
                    .run();

                return "Subtask completed: " + subtaskResult;
            })
            .build();

        String result = agent.run("Perform calculation");

        assertNotNull(result);
        assertTrue(result.contains("Subtask completed"));
        assertTrue(result.contains("30") || result.contains("thirty"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_SubtaskParallel(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        CalculatorTools calculator = new CalculatorTools();
        List<Tool<?, ?>> tools = List.of(calculator.getTool("add"), calculator.getTool("multiply"));

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a coordinator that delegates calculations.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                String subtaskResult = context.subtask("Calculate 5 + 3 and 4 * 6 using available tools")
                    .withInput(input)
                    .withOutput(String.class)
                    .withTools(tools)
                    .useLLM(model)
                    .runMode(ToolCalls.PARALLEL)
                    .run();

                return "Parallel subtask result: " + subtaskResult;
            })
            .build();

        String result = agent.run("Perform parallel calculations");

        assertNotNull(result);
        assertTrue(result.contains("Parallel subtask result"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-669")
    public void integration_SubtaskSingleRunSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        CalculatorTools calculator = new CalculatorTools();
        List<Tool<?, ?>> tools = List.of(calculator.getTool("add"));

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a coordinator.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                String subtaskResult = context.subtask("Add 7 and 8")
                    .withInput(input)
                    .withOutput(String.class)
                    .withTools(tools)
                    .useLLM(model)
                    .runMode(ToolCalls.SINGLE_RUN_SEQUENTIAL)
                    .run();

                return "Single-run result: " + subtaskResult;
            })
            .build();

        String result = agent.run("Calculate");

        assertNotNull(result);
        assertTrue(result.contains("Single-run result"));
        assertTrue(result.contains("15") || result.contains("fifteen"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ExecuteMultipleToolsParallel(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        CalculatorTools calculator = new CalculatorTools();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. Use add and multiply tools.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Response response = context.requestLLM(
                    "Calculate 5+3 and 4*6. You can use multiple tools in parallel.",
                    true
                );

                if (response instanceof Message.Tool.Call) {
                    List<Message.Tool.Call> calls = new ArrayList<>();
                    calls.add((Message.Tool.Call) response);

                    if (!calls.isEmpty()) {
                        var results = context.executeMultipleTools(calls, true);
                        var responses = context.sendMultipleToolResults(results);
                        if (!responses.isEmpty() && responses.get(0) instanceof Message.Assistant) {
                            return responses.get(0).getContent();
                        }
                    }
                }

                return "Parallel execution completed";
            })
            .build();

        String result = agent.run("Calculate");

        assertNotNull(result);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ExecuteSingleTool(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        CalculatorTools calculator = new CalculatorTools();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You coordinate tool execution.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Response response = context.requestLLM(
                    "Use the add tool to calculate 10 + 5",
                    true
                );

                if (response instanceof Message.Tool.Call) {
                    Message.Tool.Call toolCall = (Message.Tool.Call) response;
                    var toolResult = context.executeTool(toolCall);
                    var finalResponse = context.sendToolResult(toolResult);

                    if (finalResponse instanceof Message.Assistant) {
                        return finalResponse.getContent();
                    }
                }

                return "Tool execution completed";
            })
            .build();

        String result = agent.run("Test");

        assertNotNull(result);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GetHistory(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                context.requestLLM("First question: What is 2+2?", true);
                context.requestLLM("Second question: What is 3*3?", true);

                try {
                    var history = context.getHistory();
                    int historySize = history.size();

                    return "History contains " + historySize + " messages";
                } catch (Exception e) {
                    throw new RuntimeException("Failed to retrieve history", e);
                }
            })
            .build();

        String result = agent.run("Test");

        assertNotNull(result);
        assertTrue(result.contains("History contains"));
        assertTrue(result.matches(".*History contains \\d+ messages.*"));
    }
}
