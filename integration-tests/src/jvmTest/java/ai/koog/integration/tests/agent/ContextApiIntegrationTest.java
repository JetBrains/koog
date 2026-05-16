package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.environment.ReceivedToolResult;
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
import ai.koog.prompt.message.MessagePart;
import ai.koog.serialization.kotlinx.KotlinxSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

public class ContextApiIntegrationTest extends KoogJavaTestBase {

    private static String textContent(Message m) {
        return m.getParts().stream()
            .filter(p -> p instanceof MessagePart.Text)
            .map(p -> ((MessagePart.Text) p).getText())
            .collect(Collectors.joining("\n"));
    }

    private static Optional<MessagePart.Tool.Call> firstToolCall(Message.Assistant response) {
        return response.getParts().stream()
            .filter(p -> p instanceof MessagePart.Tool.Call)
            .map(p -> (MessagePart.Tool.Call) p)
            .findFirst();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
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

                    Message.Assistant response2 = session.requestLLM();
                    return textContent(response2);
                })
            )
            .build();

        String result = agent.run("Test");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("6") || result.contains("six"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_LLMReadSession(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Assistant response = context.requestLLM("What is 5+5?");

                return context.llm().readSession(session -> {
                    List<Message> messages = session.getPrompt().getMessages();
                    int historySize = messages.size();

                    return "History size: " + historySize + ", Answer: " + textContent(response);
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_SubtaskSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();
        List<Tool<?, ?>> tools = List.of(calculator.getTool("add"));

        AIAgent<String, String> agent = AIAgent.builder()
            .agentConfig(
                AIAgentConfig.builder()
                    .model(model)
                    .serializer(new KotlinxSerializer())
                    .build()
            )
            .promptExecutor(createExecutor(model))
            .systemPrompt("You are a coordinator that delegates calculations.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                String subtaskResult = context.subtask("Calculate the sum of 10 and 20 using the add tool")
                    .withOutput(String.class)
                    .withTools(tools)
                    .useLLM(model)
                    .parallelTools(false)
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_SubtaskParallel(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();
        List<Tool<?, ?>> tools = List.of(calculator.getTool("add"), calculator.getTool("multiply"));

        AIAgent<String, String> agent = AIAgent.builder()
            .agentConfig(
                AIAgentConfig.builder()
                    .model(model)
                    .serializer(new KotlinxSerializer())
                    .build()
            )
            .promptExecutor(createExecutor(model))
            .systemPrompt("You are a coordinator that delegates calculations.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                String subtaskResult = context.subtask("Calculate 5 + 3 and 4 * 6 using available tools")
                    .withOutput(String.class)
                    .withTools(tools)
                    .useLLM(model)
                    .parallelTools(true)
                    .run();

                return "Parallel subtask result: " + subtaskResult;
            })
            .build();

        String result = agent.run("Perform parallel calculations");

        assertNotNull(result);
        assertTrue(result.contains("Parallel subtask result"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_SubtaskSingleRunSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();
        List<Tool<?, ?>> tools = List.of(calculator.getTool("add"));

        AIAgent<String, String> agent = AIAgent.builder()
            .agentConfig(
                AIAgentConfig.builder()
                    .model(model)
                    .serializer(new KotlinxSerializer())
                    .build()
            )
            .promptExecutor(createExecutor(model))
            .systemPrompt("You are a coordinator.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                String subtaskResult = context.subtask("Add 7 and 8")
                    .withOutput(String.class)
                    .withTools(tools)
                    .useLLM(model)
                    .parallelTools(false)
                    .assistantResponseRepeatMax(1)
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_ExecuteMultipleToolsParallel(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. You MUST use add and multiply tools. DO NOT answer without calling tools.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Assistant response = context.requestLLM(
                    "Calculate 5+3 and 4*6. You can use multiple tools in parallel."
                );

                List<MessagePart.Tool.Call> calls = response.getParts().stream()
                    .filter(p -> p instanceof MessagePart.Tool.Call)
                    .map(p -> (MessagePart.Tool.Call) p)
                    .collect(Collectors.toCollection(ArrayList::new));

                if (!calls.isEmpty()) {
                    List<ReceivedToolResult> results = context.executeTools(calls, true);
                    Message.Assistant finalResponse = context.sendToolResults(results);
                    return textContent(finalResponse);
                }

                return "Parallel execution completed";
            })
            .build();

        String result = agent.run("Calculate");

        assertNotNull(result);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_ExecuteSingleTool(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You coordinate tool execution.")
            .toolRegistry(ToolRegistry.builder().tools(calculator).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Assistant response = context.requestLLM(
                    "Use the add tool to calculate 10 + 5"
                );

                Optional<MessagePart.Tool.Call> maybeToolCall = firstToolCall(response);
                if (maybeToolCall.isPresent()) {
                    ReceivedToolResult toolResult = context.executeTool(maybeToolCall.get());
                    Message.Assistant finalResponse = context.sendToolResult(toolResult);
                    return textContent(finalResponse);
                }

                return "Tool execution completed";
            })
            .build();

        String result = agent.run("Test");

        assertNotNull(result);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_GetHistory(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                context.requestLLM("First question: What is 2+2?");
                context.requestLLM("Second question: What is 3*3?");

                List<Message> history = context
                    .getLlm()
                    .readSession(it -> it.getPrompt().getMessages());
                int historySize = history.size();

                return "History contains " + historySize + " messages";
            })
            .build();

        String result = agent.run("Test");

        assertNotNull(result);
        assertTrue(result.contains("History contains"));
        assertTrue(result.matches(".*History contains \\d+ messages.*"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
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
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
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
