package ai.koog.agents.planner.goap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GoalBuilderJavaTest {

    @Test
    public void testBuildGoal() {
        Goal<String> goal = Goal.<String>builder()
            .name("test-goal")
            .description("test-description")
            .condition(state -> true)
            .cost(state -> 10.0)
            .value(cost -> cost * 2.0)
            .build();

        assertEquals("test-goal", goal.getName());
        assertEquals("test-description", goal.getDescription());
    }

    @Test
    public void testMissingNameFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            Goal.<String>builder()
                .condition(state -> true)
                .cost(state -> 1.0)
                .build();
        });
    }

    @Test
    public void testMissingConditionFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            Goal.<String>builder()
                .name("test")
                .cost(state -> 1.0)
                .build();
        });
    }
}
