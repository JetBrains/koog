import ai.koog.prompt.params.LLMParams;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonPrimitive;

import java.util.Map;
import java.util.List;

public class ExampleLLMParameters04 {
    public static void main(String[] args) {
        // Create parameters with a basic JSON schema
        LLMParams jsonParams = new LLMParams(
            0.2,         // temperature
            null,        // maxTokens
            1,           // numberOfChoices
            null,        // speculation
            new LLMParams.Schema.JSON.Basic(
                "PersonInfo",
                new JsonObject(Map.of(
                    "type", new JsonPrimitive("object"),
                    "properties", new JsonObject(Map.of(
                        "name", new JsonObject(Map.of("type", new JsonPrimitive("string"))),
                        "age", new JsonObject(Map.of("type", new JsonPrimitive("number"))),
                        "skills", new JsonObject(Map.of(
                            "type", new JsonPrimitive("array"),
                            "items", new JsonObject(Map.of("type", new JsonPrimitive("string")))
                        ))
                    )),
                    "additionalProperties", new JsonPrimitive(false),
                    "required", new JsonArray(List.of(
                        new JsonPrimitive("name"),
                        new JsonPrimitive("age"),
                        new JsonPrimitive("skills")
                    ))
                ))
            ),
            LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
            null,        // user
            null         // additionalProperties
        );
    }
}
