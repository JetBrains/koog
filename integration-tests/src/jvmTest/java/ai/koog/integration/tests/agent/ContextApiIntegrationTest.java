package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.ToolCalls;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.NumberTools;
import ai.koog.integration.tests.utils.JavaUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.integration.tests.utils.StructuredResults;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ContextApiIntegrationTest extends KoogJavaTestBase {
    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_RequestLLMStructuredSimple(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant that provides structured responses.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                StructuredResults.CalculationResult calc = JavaUtils.requestLLMStructuredBlocking(
                    context,
                    "Calculate 15 + 27 and return the result in the specified format",
                    StructuredResults.CalculationResult.class
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
    public void integration_RequestLLMStructuredComplex(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant that provides structured responses about people.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                StructuredResults.PersonInfo person = JavaUtils.requestLLMStructuredBlocking(
                    context,
                    "Create a person profile with name 'Alice', age 30, and hobbies: reading, coding, hiking",
                    StructuredResults.PersonInfo.class
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
    public void integration_SubtaskSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();
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
    public void integration_SubtaskParallel(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();
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
    public void integration_SubtaskSingleRunSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();
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

        NumberTools calculator = new NumberTools();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. You MUST use add and multiply tools. DO NOT answer without calling tools.")
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

        NumberTools calculator = new NumberTools();

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

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldExecuteMultipleHandlersInOrder(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicBoolean firstHandlerExecuted = new AtomicBoolean(false);
        AtomicBoolean secondHandlerExecuted = new AtomicBoolean(false);
        AtomicBoolean contextProvided = new AtomicBoolean(false);

        AtomicInteger executionOrder = new AtomicInteger(0);
        AtomicInteger firstHandlerOrder = new AtomicInteger(-1);
        AtomicInteger secondHandlerOrder = new AtomicInteger(-1);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .install(EventHandler.Feature, config ->
                config.onAgentStarting(ctx -> {
                    firstHandlerExecuted.set(true);
                    firstHandlerOrder.set(executionOrder.getAndIncrement());
                })
            )
            .install(EventHandler.Feature, config ->
                config.onAgentStarting(ctx -> {
                    if (ctx != null) {
                        contextProvided.set(true);
                    }
                    secondHandlerExecuted.set(true);
                    secondHandlerOrder.set(executionOrder.getAndIncrement());
                })
            )
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(contextProvided).isTrue();
        assertThat(firstHandlerExecuted).isTrue();
        assertThat(secondHandlerExecuted).isTrue();
        assertThat(firstHandlerOrder.get()).isLessThan(secondHandlerOrder.get());
        assertThat(firstHandlerOrder.get()).isEqualTo(0);
        assertThat(secondHandlerOrder.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldSupportMultipleHandlers(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger firstHandlerCalls = new AtomicInteger(0);
        AtomicInteger secondHandlerCalls = new AtomicInteger(0);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> firstHandlerCalls.incrementAndGet());
                config.onAgentCompleted(ctx -> firstHandlerCalls.incrementAndGet());
            })
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> secondHandlerCalls.incrementAndGet());
                config.onAgentCompleted(ctx -> secondHandlerCalls.incrementAndGet());
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));
        assertThat(firstHandlerCalls.get()).isEqualTo(2);
        assertThat(secondHandlerCalls.get()).isEqualTo(2);

        runBlocking(continuation -> agent.run("Hello", null, continuation));
        assertThat(firstHandlerCalls.get()).isEqualTo(4);
        assertThat(secondHandlerCalls.get()).isEqualTo(4);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldConfigureMultipleEvents(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger agentStartCount = new AtomicInteger(0);
        AtomicInteger llmCallCount = new AtomicInteger(0);
        AtomicInteger agentCompleteCount = new AtomicInteger(0);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> agentStartCount.incrementAndGet());
                config.onLLMCallStarting(ctx -> llmCallCount.incrementAndGet());
                config.onAgentCompleted(ctx -> agentCompleteCount.incrementAndGet());
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(agentStartCount.get()).isEqualTo(1);
        assertThat(llmCallCount.get()).isGreaterThan(0);
        assertThat(agentCompleteCount.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldTriggerToolEventsInOrder(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        List<String> eventOrder = new ArrayList<>();
        NumberTools calculator = new NumberTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculator).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are an assistant with calculator tools. IMPORTANT: " +
                "You do NOT have access to random number generation - you MUST use the generateRandomNumber tool. " +
                "You MUST use the add tool for any addition operations. " +
                "You cannot perform these operations yourself. ALWAYS use the provided tools.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> eventOrder.add("AgentStarting"));
                config.onLLMCallStarting(ctx -> eventOrder.add("LLMCallStarting"));
                config.onToolCallStarting(ctx -> eventOrder.add("ToolCallStarting:" + ctx.getToolName()));
                config.onToolCallCompleted(ctx -> eventOrder.add("ToolCallCompleted:" + ctx.getToolName()));
                config.onLLMCallCompleted(ctx -> eventOrder.add("LLMCallCompleted"));
                config.onAgentCompleted(ctx -> eventOrder.add("AgentCompleted"));
            })
            .build();

        String result = runBlocking(continuation -> agent.run(
            "Generate a random number, then add 5 to it. You must use the tools.",
            null,
            continuation
        ));

        assertThat(result).isNotNull();
        assertThat(eventOrder).isNotEmpty();
        assertThat(eventOrder.get(0)).isEqualTo("AgentStarting");
        assertThat(eventOrder.get(eventOrder.size() - 1)).isEqualTo("AgentCompleted");

        boolean hasToolCallStarting = eventOrder.stream().anyMatch(e -> e.startsWith("ToolCallStarting:"));
        boolean hasToolCallCompleted = eventOrder.stream().anyMatch(e -> e.startsWith("ToolCallCompleted:"));
        boolean hasGenerateRandomNumber = eventOrder.stream().anyMatch(e -> e.contains("generateRandomNumber"));

        assertThat(hasToolCallStarting).isTrue();
        assertThat(hasToolCallCompleted).isTrue();
        assertThat(hasGenerateRandomNumber).isTrue();
    }
}
