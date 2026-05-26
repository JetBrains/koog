package ai.koog.agents.planner;

import ai.koog.agents.planner.goap.GoapAgentState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlannersBuilderJavaTest {
    static class SimpleState extends GoapAgentState<String, String> {
        private boolean completed = false;
        private final String agentInput;

        SimpleState(String input) {
            this.agentInput = input;
        }

        @Override
        public String getAgentInput() {
            return agentInput;
        }

        SimpleState copy(boolean completed) {
            var newState = new SimpleState(getAgentInput());
            newState.completed = completed;
            return newState;
        }

        @Override
        public String provideOutput() {
            return getAgentInput();
        }

        boolean isCompleted() {
            return completed;
        }
    }

    @Test
    public void testGoapBuilderCreatesAgent() {
        var strategy = Planners.goap("goap-strategy", SimpleState::new)
            .action("complete", builder -> builder
                .precondition(state -> !state.isCompleted())
                .belief(state -> state.copy(true))
                .execute((context, state) -> state.copy(true))
            )
            .goal("isDone", builder -> builder
                .condition(state -> state.isCompleted())
            )
            .build();

        assertEquals("goap-strategy", strategy.getName());
    }

    @Test
    public void testLlmBasedBuilderCreatesAgent() {
        var strategy = Planners.llmBased("llm-strategy").build();

        assertEquals("llm-strategy", strategy.getName());
    }

    @Test
    public void testLlmBasedWithCriticBuilderCreatesAgent() {
        var strategy = Planners.llmBasedWithCritic("llm-critic-strategy").build();

        assertEquals("llm-critic-strategy", strategy.getName());
    }
}
