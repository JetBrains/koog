package ai.koog.agents.core.agent;

import ai.koog.agents.core.agent.entity.AIAgentStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaAIAgentStorageTest {

    @Test
    void testUseStorage() {
        var storage = new AIAgentStorage();
        var stringKey = AIAgentStorage.<String>createStorageKey("test");
        var value = "test value";

        storage.set(stringKey, value);
        assertEquals(value, storage.get(stringKey));
    }
}
