import ai.koog.prompt.params.LLMParams;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonPrimitive;
import kotlinx.serialization.json.JsonArray;

import java.util.Map;
import java.util.List;

public class ExampleLLMParameters05 {
    public static void main(String[] args) {
        // Create parameters with a standard JSON schema
        LLMParams standardJsonParams = new LLMParams(
            0.2,         // temperature
            null,        // maxTokens
            1,           // numberOfChoices
            null,        // speculation
            new LLMParams.Schema.JSON.Standard(
                "ProductCatalog",
                new JsonObject(Map.of(
                    "type", new JsonPrimitive("object"),
                    "properties", new JsonObject(Map.of(
                        "products", new JsonObject(Map.of(
                            "type", new JsonPrimitive("array"),
                            "items", new JsonObject(Map.of(
                                "type", new JsonPrimitive("object"),
                                "properties", new JsonObject(Map.of(
                                    "id", new JsonObject(Map.of("type", new JsonPrimitive("string"))),
                                    "name", new JsonObject(Map.of("type", new JsonPrimitive("string"))),
                                    "price", new JsonObject(Map.of("type", new JsonPrimitive("number"))),
                                    "description", new JsonObject(Map.of("type", new JsonPrimitive("string")))
                                )),
                                "additionalProperties", new JsonPrimitive(false),
                                "required", new JsonArray(List.of(
                                    new JsonPrimitive("id"),
                                    new JsonPrimitive("name"),
                                    new JsonPrimitive("price"),
                                    new JsonPrimitive("description")
                                ))
                            ))
                        ))
                    )),
                    "additionalProperties", new JsonPrimitive(false),
                    "required", new JsonArray(List.of(new JsonPrimitive("products")))
                ))
            ),
            LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
            null,        // user
            null         // additionalProperties
        );
    }
}
