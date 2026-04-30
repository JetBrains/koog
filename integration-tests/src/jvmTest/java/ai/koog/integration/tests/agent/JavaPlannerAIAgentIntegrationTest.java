package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentPlannerContext;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.ToolRegistryBuilder;
import ai.koog.agents.planner.AIAgentPlannerStrategy;
import ai.koog.agents.planner.JavaAIAgentPlanner;
import ai.koog.agents.planner.PlannerAIAgent;
import ai.koog.agents.planner.goap.Action;
import ai.koog.agents.planner.goap.Goal;
import ai.koog.agents.planner.goap.GoapAgentState;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.NumberTools;
import ai.koog.integration.tests.utils.annotations.Retry;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.message.Message;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaPlannerAIAgentIntegrationTest extends KoogJavaTestBase {
    static class TestPlanner extends JavaAIAgentPlanner<String, String> {

        @Override
        protected String buildPlan(AIAgentPlannerContext context, String state, @Nullable String plan) {
            return "Request llm with state.";
        }

        @Override
        protected String executeStep(AIAgentPlannerContext context, String state, String plan) {
            Message.Response response = context.requestLLM(state, true);

            int maxIterations = 5;
            for (int i = 0; i < maxIterations && response instanceof Message.Tool.Call; i++) {
                response = context.sendToolResult(context.executeTool((Message.Tool.Call) response));
            }

            if (response instanceof Message.Assistant) {
                return response.getContent();
            } else if (response instanceof Message.Tool.Call) {
                return "Max iterations reached, last tool: " + ((Message.Tool.Call) response).getTool();
            }
            return response.getContent();
        }

        @Override
        protected Boolean isPlanCompleted(AIAgentPlannerContext context, String state, String plan) {
            return !state.equals(context.getAgentInput());
        }
    }

    private static final String SYSTEM_PROMPT = "You are a helpful assistant.";
    private static final String REQUEST = "What's 1 + 1?";

    private void testPlanner(AIAgentPlannerStrategy<String, String, ?> strategy) {
        testPlanner(strategy, null, REQUEST, "2");
    }

    private void testPlanner(
        AIAgentPlannerStrategy<String, String, ?> strategy,
        @Nullable ToolRegistry toolRegistry,
        String request,
        String expectedResultPart
    ) {
        var builder = PlannerAIAgent.<String, String>builder(strategy)
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
        var planner = AIAgentPlannerStrategy.builder("simple").llmBasedPlanner().build();

        testPlanner(planner);
    }

    @Test
    @Retry
    public void integration_testPlannerWithTools() {
        var planner = new TestPlanner();
        var plannerStrategy = AIAgentPlannerStrategy.builder("test-planner").withPlanner(planner).build();
        var toolRegistry = new ToolRegistryBuilder()
            .tools(new NumberTools())
            .build();

        testPlanner(plannerStrategy, toolRegistry, "How much is 123 + 456?", "579");
    }

    private class TextualState extends GoapAgentState<String, String> {
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
        var formulateAction = Action.<TextualState>builder()
            .name("formulate-problem")
            .precondition(state -> true)
            .belief(state -> new TextualState("Problem: example problem"))
            .execute((context, state) -> {
                String result = context.llm().writeSession(session -> {
                    session.setPrompt(Prompt.builder("tmp").system(SYSTEM_PROMPT).user("Formulate problem: " + state.text + ". Answer with the problem formulation in the form \"Problem: ...\"").build());
                    return session.requestLLM().getContent();
                });
                return new TextualState(result);
            })
            .build();

        var solveAction = Action.<TextualState>builder()
            .name("solve-problem")
            .precondition(state -> state.text.contains("Problem"))
            .belief(state -> new TextualState("Solution: example solution"))
            .execute((context, state) -> {
                String result = context.llm().writeSession(session -> {
                    session.setPrompt(Prompt.builder("tmp").system(SYSTEM_PROMPT).user("Find solution. " + state.text + ". Answer with the solution in the form \"Solution: ...\"").build());
                    return session.requestLLM().getContent();
                });
                return new TextualState(result);
            })
            .build();

        var goal = Goal.<TextualState>builder()
            .name("find-solution")
            .cost(state -> 1.0)
            .condition(state -> state.text.contains("Solution"))
            .build();


        var planner = AIAgentPlannerStrategy.builder("custom-goap")
            .goap(TextualState::new)
            .action(formulateAction)
            .action(solveAction)
            .goal(goal)
            .build();

        testPlanner(planner);
    }
}
