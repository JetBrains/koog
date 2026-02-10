package ai.koog.serialization.jackson;

import ai.koog.serialization.TypeToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaJacksonSerializerTest {
    private final JacksonSerializer serializer = new JacksonSerializer();

    @Test
    void testPrimitiveSerialization() {
        var typeToken = TypeToken.of(String.class);

        assertEquals(
            "\"test\"",
            serializer.encodeToString("test", typeToken)
        );
    }
}
