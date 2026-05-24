package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentPlannerContext;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.ToolRegistryBuilder;
import ai.koog.agents.core.planner.AIAgentPlannerStrategy;
import ai.koog.agents.core.planner.JavaAIAgentPlanner;
import ai.koog.agents.planner.Planners;
import ai.koog.agents.planner.goap.GoapAgentState;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.NumberTools;
import ai.koog.integration.tests.utils.annotations.Retry;
import ai.koog.prompt.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.MessagePart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaPlannerAIAgentIntegrationTest extends KoogJavaTestBase {
    static class TestPlanner extends JavaAIAgentPlanner<String, String, String, String> {

        @Override
        protected @NotNull String initializeState(String input) {
            return input;
        }

        @Override
        protected String provideOutput(@NotNull String state) {
            return state;
        }

        @Override
        protected String buildPlan(AIAgentPlannerContext context, String state, @Nullable String plan) {
            return "Request llm with state.";
        }

        @Override
        protected String executeStep(AIAgentPlannerContext context, String state, String plan) {
            Message.Assistant response = context.requestLLM(state);

            int maxIterations = 5;
            for (int i = 0; i < maxIterations; i++) {
                Optional<MessagePart.Tool.Call> maybeToolCall = firstToolCall(response);
                if (maybeToolCall.isEmpty()) {
                    break;
                }
                response = context.sendToolResult(context.executeTool(maybeToolCall.get()));
            }

            Optional<MessagePart.Tool.Call> maybeToolCall = firstToolCall(response);
            if (maybeToolCall.isPresent()) {
                return "Max iterations reached, last tool: " + maybeToolCall.get().getTool();
            }
            return assistantText(response);
        }

        @Override
        protected Boolean isPlanCompleted(AIAgentPlannerContext context, String state, String plan) {
            return !state.equals(context.getAgentInput());
        }
    }

    private static final String SYSTEM_PROMPT = "You are a helpful assistant.";
    private static final String REQUEST = "What's 1 + 1?";

    private void testPlanner(AIAgentPlannerStrategy<String, String> strategy) {
        testPlanner(strategy, null, REQUEST, "2");
    }

    private void testPlanner(
        AIAgentPlannerStrategy<String, String> strategy,
        ToolRegistry toolRegistry,
        String request,
        String expectedResultPart
    ) {
        var builder = AIAgent.builder().<String, String>plannerStrategy(strategy)
            .promptExecutor(createExecutor(OpenAIModels.Chat.GPT5_1))
            .llmModel(OpenAIModels.Chat.GPT5_1)
            .systemPrompt(SYSTEM_PROMPT);

        if (toolRegistry != null) {
            builder.toolRegistry(toolRegistry);
        }

        AIAgent<String, String> agent = builder.build();

        assertNotNull(agent);

        String result = agent.run(request);

        assertNotNull(result);
        assertTrue(result.contains(expectedResultPart), "Result should contain: " + expectedResultPart + ", but was: " + result);
    }

    @Test
    @Retry
    public void integration_testSimplePlanner() {
        var planner = Planners.llmBased("simple").build();

        testPlanner(planner);
    }

    @Test
    @Retry
    public void integration_testPlannerWithTools() {
        var planner = new TestPlanner();
        var plannerStrategy = AIAgentPlannerStrategy.create("test-planner", planner);
        var toolRegistry = new ToolRegistryBuilder()
            .tools(new NumberTools())
            .build();

        testPlanner(plannerStrategy, toolRegistry, "How much is 123 + 456?", "579");
    }

    private static class TextualState extends GoapAgentState<String, String> {
        public String text;

        public TextualState(String text) {
            this.text = text;
        }

        @Override
        public String getAgentInput() {
            return text;
        }

        @Override
        public String provideOutput() {
            return text;
        }
    }

    @Test
    @Retry
    public void integration_testGoapPlanner() {
        var planner = Planners.goap("custom-goap", TextualState::new)
            .action("formulate-problem", builder -> builder
                .precondition(state -> true)
                .belief(state -> new TextualState("Problem: example problem"))
                .execute((context, state) -> {
                    String result = context.llm().writeSession(session -> {
                        session.setPrompt(Prompt.builder("tmp").system(SYSTEM_PROMPT).user("Formulate problem: " + state.text + ". Answer with the problem formulation in the form \"Problem: ...\"").build());
                        return assistantText(session.requestLLM());
                    });
                    return new TextualState(result);
                })
            )
            .action("solve-problem", builder -> builder
                .precondition(state -> state.text.contains("Problem"))
                .belief(state -> new TextualState("Solution: example solution"))
                .execute((context, state) -> {
                    String result = context.llm().writeSession(session -> {
                        session.setPrompt(Prompt.builder("tmp").system(SYSTEM_PROMPT).user("Find solution. " + state.text + ". Answer with the solution in the form \"Solution: ...\"").build());
                        return assistantText(session.requestLLM());
                    });
                    return new TextualState(result);
                })
            )
            .goal("find-solution", builder -> builder
                .cost(state -> 1.0)
                .condition(state -> state.text.contains("Solution"))
            )
            .build();

        testPlanner(planner);
    }

    private static String assistantText(Message.Assistant response) {
        return response.getParts().stream()
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
}
