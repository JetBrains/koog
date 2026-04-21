package ai.koog.agents.planner.goap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ActionBuilderJavaTest {

    @Test
    public void testBuildAction() {
        Action<String> action = Action.<String>builder()
            .name("test-action")
            .description("test-description")
            .precondition(state -> true)
            .belief((state) -> "end")
            .execute((context, state) -> "executed")
            .build();

        assertEquals("test-action", action.getName());
        assertEquals("test-description", action.getDescription());
    }

    @Test
    public void testMissingNameFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            Action.<String>builder()
                .precondition(state -> true)
                .belief(state -> state)
                .execute((context, state) -> state)
                .build();
        });
    }

    @Test
    public void testMissingPreconditionFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            Action.<String>builder()
                .name("test")
                .belief(state -> state)
                .execute((context, state) -> state)
                .build();
        });
    }

    @Test
    public void testMissingBeliefFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            Action.<String>builder()
                .name("test")
                .precondition(state -> true)
                .execute((context, state) -> state)
                .build();
        });
    }

    @Test
    public void testMissingExecuteFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            Action.<String>builder()
                .name("test")
                .precondition(state -> true)
                .belief(state -> state)
                .build();
        });
    }
}
