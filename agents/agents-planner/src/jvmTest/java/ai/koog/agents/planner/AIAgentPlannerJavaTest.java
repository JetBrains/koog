package ai.koog.agents.planner;

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

public class AIAgentPlannerJavaTest {

    public class MockAIAgentPlanner extends JavaAIAgentPlanner<String, String> {

        @Override
        protected String buildPlan(
            AIAgentFunctionalContext context,
            String state,
            @Nullable String plan
        ) {
            return "plan";
        }

        @Override
        protected String executeStep(
            AIAgentFunctionalContext context,
            String state,
            String plan
        ) {
            return "state";
        }

        @Override
        protected Boolean isPlanCompleted(
            AIAgentFunctionalContext context,
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
