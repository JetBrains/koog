package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.ToolRegistryBuilder;
import ai.koog.agents.planner.AIAgentPlanner;
import ai.koog.agents.planner.AIAgentPlannerStrategy;
import ai.koog.agents.planner.JavaAIAgentPlanner;
import ai.koog.agents.planner.PlannerAIAgent;
import ai.koog.agents.planner.goap.Action;
import ai.koog.agents.planner.goap.GOAPPlanner;
import ai.koog.agents.planner.goap.GOAPPlannerBuilder;
import ai.koog.agents.planner.goap.Goal;
import ai.koog.agents.planner.llm.SimpleLLMPlanner;
import ai.koog.agents.planner.llm.SimplePlan;
import ai.koog.integration.tests.utils.TestCredentials;
import ai.koog.integration.tests.utils.annotations.Retry;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.model.PromptExecutor;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaPlannerAIAgentIntegrationTest {
    static class TestPlanner extends JavaAIAgentPlanner<String, String> {

        @Override
        protected String buildPlan(AIAgentFunctionalContext context, String state, @Nullable String plan) {
            return "Request llm with state.";
        }

        @Override
        protected String executeStep(AIAgentFunctionalContext context, String state, String plan) {
            return context.llm().writeSession(session -> {
                session.setPrompt(Prompt.builder("tmp").system(SYSTEM_PROMPT).user(state).build());
                return session.requestLLM().getContent();
            });
        }

        @Override
        protected Boolean isPlanCompleted(AIAgentFunctionalContext context, String state, String plan) {
            return !state.equals(context.getAgentInput());
        }
    }

    private static final String STRATEGY_NAME = "my-strategy";

    private static final LLMClient client = new OpenAILLMClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
    private static final PromptExecutor promptExecutor = new MultiLLMPromptExecutor(client);
    private static final String SYSTEM_PROMPT = "You are a helpful assistant.";
    private static final String REQUEST = "What's 1 + 1?";

    @SuppressWarnings("unchecked")
    private static <Plan> void testPlanner(AIAgentPlanner<String, Plan> planner) {
        testPlanner(planner, null, REQUEST, "2");
    }

    @SuppressWarnings("unchecked")
    private static <Plan> void testPlanner(AIAgentPlanner<String, Plan> planner, @Nullable ToolRegistry toolRegistry, String request, String expectedResultPart) {
        AIAgentPlannerStrategy<String, Plan> strategy = new AIAgentPlannerStrategy<String, Plan>(STRATEGY_NAME, planner);
        var builder = PlannerAIAgent.<String, Plan>builder(strategy)
            .promptExecutor(promptExecutor)
            .llmModel(OpenAIModels.Chat.GPT4o)
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
        AIAgentPlanner<String, SimplePlan> planner = new SimpleLLMPlanner();

        testPlanner(planner);
    }

    @Test
    @Retry
    public void integration_testPlannerWithTools() {
        AIAgentPlanner<String, String> planner = new TestPlanner();
        ToolRegistry toolRegistry = new ToolRegistryBuilder()
            .tools(new CalculatorTools())
            .build();

        testPlanner(planner, toolRegistry, "How much is 123 + 456?", "{\"a\":123,\"b\":456}");
    }

    @Test
    @Retry
    public void integration_testGoapPlanner() {
        Action<String> formulateAction = Action.<String>builder()
            .name("formulate-problem")
            .precondition(state -> true)
            .belief(state -> "Problem: example problem")
            .executeSync((context, state) -> context.llm().writeSession(session -> {
                session.setPrompt(Prompt.builder("tmp").system(SYSTEM_PROMPT).user("Formulate problem: " + state + ". Answer with the problem formulation in the form \"Problem: ...\"").build());
                return session.requestLLM().getContent();
            }))
            .build();

        Action<String> solveAction = Action.<String>builder()
            .name("solve-problem")
            .precondition(state -> state.contains("Problem"))
            .belief(state -> "Solution: example solution")
            .executeSync((context, state) -> context.llm().writeSession(session -> {
                session.setPrompt(Prompt.builder("tmp").system(SYSTEM_PROMPT).user("Find solution. " + state + ". Answer with the solution in the form \"Solution: ...\"").build());
                return session.requestLLM().getContent();
            }))
            .build();

        Goal<String> goal = Goal.<String>builder()
            .name("find-solution")
            .cost(state -> 1.0)
            .condition(state -> state.contains("Solution"))
            .build();

        GOAPPlannerBuilder<String> builder = new GOAPPlannerBuilder<String>();

        GOAPPlanner<String> planner = builder
            .action(formulateAction)
            .action(solveAction)
            .goal(goal)
            .build();

        testPlanner(planner);
    }
}
