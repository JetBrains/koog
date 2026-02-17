package ai.koog.agents.planner;

import ai.koog.agents.core.agent.context.AIAgentPlannerContext;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

public class AIAgentPlannerJavaTest {

    public class MockAIAgentPlanner extends JavaAIAgentPlanner<String, String> {

        @Override
        protected String buildPlan(
            AIAgentPlannerContext context,
            String state,
            @Nullable String plan
        ) {
            return "plan";
        }

        @Override
        protected String executeStep(
            AIAgentPlannerContext context,
            String state,
            String plan
        ) {
            return "state";
        }

        @Override
        protected Boolean isPlanCompleted(
            AIAgentPlannerContext context,
            String state,
            String plan
        ) {
            return state.equals("state");
        }
    }

    @Test
    public void testJavaAIAgentPlanner() {
        MockAIAgentPlanner planner = new MockAIAgentPlanner();
    }
}
