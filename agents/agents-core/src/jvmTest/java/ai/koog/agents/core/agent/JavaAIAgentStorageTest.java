package ai.koog.agents.core.agent;

import ai.koog.agents.core.agent.entity.AIAgentStorage;
import ai.koog.serialization.TypeToken;
import ai.koog.serialization.jackson.JacksonSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaAIAgentStorageTest {

    @Test
    void testUseStorage() {
        var storage = new AIAgentStorage(new JacksonSerializer());
        var stringKey = AIAgentStorage.createStorageKey("test", TypeToken.of(String.class));
        var value = "test value";

        storage.set(stringKey, value);
        assertEquals(value, storage.get(stringKey));
    }
}
