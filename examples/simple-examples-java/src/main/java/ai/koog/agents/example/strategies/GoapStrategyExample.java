package ai.koog.agents.example.strategies;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.planner.AIAgentPlannerStrategy;
import ai.koog.agents.planner.goap.Action;
import ai.koog.agents.planner.goap.GoapAgentState;

public class GoapStrategyExample {
    static class MyState extends GoapAgentState<String, String> {
        public String foo = "";
        public double bar = 0;
        public String result = "";

        public MyState(String agentInput) {
            super(agentInput);
        }

        @Override
        public String provideOutput() {
            return result;
        }
    }


    public static void main(String[] args) {
        var strategy = AIAgentPlannerStrategy.builder("my-strategy")
            .goap(MyState::new)
            .action(
                "action-1",
                action ->
                    action
                        .description("Description for action-1")
                        .cost(state -> state.bar)
                        .precondition(state -> !state.foo.isEmpty())

            );

        AIAgent.builder()
            .plannerStrategy();
    }
}
