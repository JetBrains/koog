package ai.koog.agents.core.agent;

import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.agent.context.AIAgentPlannerContext;
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.planner.AIAgentPlannerStrategy;
import ai.koog.agents.planner.JavaAIAgentPlanner;
import ai.koog.agents.planner.PlannerAIAgent;
import ai.koog.agents.testing.tools.MockExecutorBuilder;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.processor.ResponseProcessor;
import ai.koog.serialization.jackson.JacksonSerializer;
import kotlin.time.Clock;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AIAgentFactoriesTest {

    private static final JacksonSerializer serializer = new JacksonSerializer();

    private final LLModel llmModel = OpenAIModels.Chat.GPT4o;
    private final int maxIterations = 10;
    private final Double temperature = 0.7;
    private final String systemPrompt = "You are a helpful assistant.";
    private final String id = "test-id";
    private final Clock clock = Clock.System.INSTANCE;
    private final ToolRegistry toolRegistry = ToolRegistry.builder().build();
    private final ResponseProcessor responseProcessor = null;
    private final AIAgentConfig agentConfig = AIAgentConfig.builder()
        .model(llmModel)
        .prompt(Prompt.builder("test").user("hi").build())
        .maxAgentIterations(maxIterations)
        .build();
    private final AIAgentGraphStrategy<String, String> graphStrategy = AIAgentSimpleStrategies.singleRunStrategy();
    private final AIAgentFunctionalStrategy<String, String> functionalStrategy = new TestFunctionalStrategy();
    private final AIAgentPlannerStrategy<String, String, String> plannerStrategy = new AIAgentPlannerStrategy<>(
        "test-planner", new TestPlanner(), input -> input, output -> output
    );

    private final PromptExecutor mockExecutor = new MockExecutorBuilder(serializer).mockLLMAnswer("ok").asDefaultResponse().build();

    static class TestFunctionalStrategy extends NonSuspendAIAgentFunctionalStrategy<String, String> {
        TestFunctionalStrategy() { super("test-functional"); }

        @Override
        public String executeStrategy(AIAgentFunctionalContext context, String input) {
            return input;
        }
    }

    static class TestPlanner extends JavaAIAgentPlanner<String, String> {
        @Override
        protected String buildPlan(AIAgentPlannerContext ctx, String state, @Nullable String plan) {
            return "plan";
        }

        @Override
        protected String executeStep(AIAgentPlannerContext ctx, String state, String plan) {
            return state;
        }

        @Override
        protected Boolean isPlanCompleted(AIAgentPlannerContext ctx, String state, String plan) {
            return true;
        }
    }

    // region: agentConfig-based overloads — minimal signatures

    @Test
    public void testCreateGraphAgentWithConfig() {
        GraphAIAgent<String, String> agent = AIAgentFactory.create(mockExecutor, agentConfig, graphStrategy);

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig(), agentConfig);
        assertEquals(agent.getStrategy(), graphStrategy);
    }

    @Test
    public void testCreateDefaultGraphAgentWithConfig() {
        GraphAIAgent<String, String> agent = AIAgentFactory.create(mockExecutor, agentConfig);

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig(), agentConfig);
    }

    @Test
    public void testCreateFunctionalAgentWithConfig() {
        FunctionalAIAgent<String, String> agent = AIAgentFactory.create(mockExecutor, agentConfig, functionalStrategy);

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig(), agentConfig);
        assertEquals(agent.getStrategy(), functionalStrategy);
    }

    @Test
    public void testCreatePlannerAgentWithConfig() {
        PlannerAIAgent<String, String> agent = AIAgentFactory.create(mockExecutor, agentConfig, plannerStrategy);

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig(), agentConfig);
        assertEquals(agent.getStrategy(), plannerStrategy);
    }

    // endregion

    // region: agentConfig-based overloads — full signatures

    @Test
    public void testCreateGraphAgentWithConfigFullSignature() {
        GraphAIAgent<String, String> agent = AIAgentFactory.create(
            mockExecutor, agentConfig, graphStrategy, toolRegistry, id, clock, ctx -> kotlin.Unit.INSTANCE
        );

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig(), agentConfig);
        assertEquals(agent.getStrategy(), graphStrategy);
        assertEquals(agent.getToolRegistry(), toolRegistry);
        assertEquals(agent.getId(), id);
        assertEquals(agent.getClock(), clock);
    }

    @Test
    public void testCreateDefaultGraphAgentWithConfigFullSignature() {
        GraphAIAgent<String, String> agent = AIAgentFactory.create(
            mockExecutor, agentConfig, toolRegistry, id, clock, ctx -> kotlin.Unit.INSTANCE
        );

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig(), agentConfig);
        assertEquals(agent.getToolRegistry(), toolRegistry);
        assertEquals(agent.getId(), id);
        assertEquals(agent.getClock(), clock);
    }

    @Test
    public void testCreateFunctionalAgentWithConfigFullSignature() {
        FunctionalAIAgent<String, String> agent = AIAgentFactory.create(
            mockExecutor, agentConfig, functionalStrategy, toolRegistry, id, clock, ctx -> kotlin.Unit.INSTANCE
        );

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig(), agentConfig);
        assertEquals(agent.getStrategy(), functionalStrategy);
        assertEquals(agent.getToolRegistry(), toolRegistry);
        assertEquals(agent.getId(), id);
        assertEquals(agent.getClock(), clock);
    }

    @Test
    public void testCreatePlannerAgentWithConfigFullSignature() {
        PlannerAIAgent<String, String> agent = AIAgentFactory.create(
            mockExecutor, agentConfig, plannerStrategy, toolRegistry, id, clock, ctx -> kotlin.Unit.INSTANCE
        );

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig(), agentConfig);
        assertEquals(agent.getStrategy(), plannerStrategy);
        assertEquals(agent.getToolRegistry(), toolRegistry);
        assertEquals(agent.getId(), id);
        assertEquals(agent.getClock(), clock);
    }

    // endregion

    // region: llmModel-based overloads — minimal signatures

    @Test
    public void testCreateGraphAgentWithModel() {
        GraphAIAgent<String, String> agent = AIAgentFactory.create(mockExecutor, llmModel, graphStrategy);

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig().getModel(), llmModel);
        assertEquals(agent.getStrategy(), graphStrategy);
    }

    @Test
    public void testCreateDefaultGraphAgentWithModel() {
        GraphAIAgent<String, String> agent = AIAgentFactory.create(mockExecutor, llmModel);

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig().getModel(), llmModel);
    }

    @Test
    public void testCreateFunctionalAgentWithModel() {
        FunctionalAIAgent<String, String> agent = AIAgentFactory.create(mockExecutor, llmModel, functionalStrategy);

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig().getModel(), llmModel);
        assertEquals(agent.getStrategy(), functionalStrategy);
    }

    @Test
    public void testCreatePlannerAgentWithModel() {
        PlannerAIAgent<String, String> agent = AIAgentFactory.create(mockExecutor, llmModel, plannerStrategy);

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig().getModel(), llmModel);
        assertEquals(agent.getStrategy(), plannerStrategy);
    }

    // endregion

    // region: llmModel-based overloads — full signatures

    @Test
    public void testCreateGraphAgentWithModelFullSignature() {
        GraphAIAgent<String, String> agent = AIAgentFactory.create(
            mockExecutor, llmModel, graphStrategy, toolRegistry,
            systemPrompt, temperature, maxIterations, responseProcessor, id, clock, ctx -> kotlin.Unit.INSTANCE
        );

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig().getModel(), llmModel);
        assertEquals(agent.getStrategy(), graphStrategy);
        assertEquals(agent.getToolRegistry(), toolRegistry);
        assertEquals(agent.getAgentConfig().getPrompt().getMessages().get(0).getContent(), systemPrompt);
        assertEquals(agent.getAgentConfig().getPrompt().getParams().getTemperature(), temperature);
        assertEquals(agent.getAgentConfig().getMaxAgentIterations(), maxIterations);
        assertEquals(agent.getId(), id);
        assertEquals(agent.getClock(), clock);
    }

    @Test
    public void testCreateDefaultGraphAgentWithModelFullSignature() {
        GraphAIAgent<String, String> agent = AIAgentFactory.create(
            mockExecutor, llmModel, toolRegistry,
            systemPrompt, temperature, maxIterations, responseProcessor, id, clock, ctx -> kotlin.Unit.INSTANCE
        );

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig().getModel(), llmModel);
        assertEquals(agent.getToolRegistry(), toolRegistry);
        assertEquals(agent.getAgentConfig().getPrompt().getMessages().get(0).getContent(), systemPrompt);
        assertEquals(agent.getAgentConfig().getPrompt().getParams().getTemperature(), temperature);
        assertEquals(agent.getAgentConfig().getMaxAgentIterations(), maxIterations);
        assertEquals(agent.getId(), id);
        assertEquals(agent.getClock(), clock);
    }

    @Test
    public void testCreateFunctionalAgentWithModelFullSignature() {
        FunctionalAIAgent<String, String> agent = AIAgentFactory.create(
            mockExecutor, llmModel, functionalStrategy, toolRegistry,
            systemPrompt, temperature, maxIterations, responseProcessor, id, clock, ctx -> kotlin.Unit.INSTANCE
        );

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig().getModel(), llmModel);
        assertEquals(agent.getStrategy(), functionalStrategy);
        assertEquals(agent.getToolRegistry(), toolRegistry);
        assertEquals(agent.getAgentConfig().getPrompt().getMessages().get(0).getContent(), systemPrompt);
        assertEquals(agent.getAgentConfig().getPrompt().getParams().getTemperature(), temperature);
        assertEquals(agent.getAgentConfig().getMaxAgentIterations(), maxIterations);
        assertEquals(agent.getId(), id);
        assertEquals(agent.getClock(), clock);
    }

    @Test
    public void testCreatePlannerAgentWithModelFullSignature() {
        PlannerAIAgent<String, String> agent = AIAgentFactory.create(
            mockExecutor, llmModel, plannerStrategy, toolRegistry,
            systemPrompt, temperature, maxIterations, responseProcessor, id, clock, ctx -> kotlin.Unit.INSTANCE
        );

        assertEquals(agent.getPromptExecutor(), mockExecutor);
        assertEquals(agent.getAgentConfig().getModel(), llmModel);
        assertEquals(agent.getStrategy(), plannerStrategy);
        assertEquals(agent.getToolRegistry(), toolRegistry);
        assertEquals(agent.getAgentConfig().getPrompt().getMessages().get(0).getContent(), systemPrompt);
        assertEquals(agent.getAgentConfig().getPrompt().getParams().getTemperature(), temperature);
        assertEquals(agent.getAgentConfig().getMaxAgentIterations(), maxIterations);
        assertEquals(agent.getId(), id);
        assertEquals(agent.getClock(), clock);
    }

    // endregion
}
