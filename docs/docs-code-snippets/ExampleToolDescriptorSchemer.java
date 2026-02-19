import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.koog.agents.core.tools.ToolDescriptor;
import ai.koog.agents.core.tools.ToolParameterDescriptor;
import ai.koog.agents.core.tools.ToolParameterType;
import ai.koog.agents.core.tools.serialization.ToolDescriptorSchemaGenerator;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonObject;

import static kotlinx.serialization.json.JsonElementKt.JsonPrimitive;

/**
 * Example Java snippet for ToolDescriptor schemer, generated according to the docs migration prompt.
 *
 * Contains:
 *  - MinimalSchemer Java implementation of ToolDescriptorSchemaGenerator (String, Integer, Enum; others fallback to string)
 *  - A small demo main() that builds a ToolDescriptor and prints produced schema
 */
public class ExampleToolDescriptorSchemer {

    // Minimal Java implementation mirroring the Kotlin example in the docs
    public static class MinimalSchemer implements ToolDescriptorSchemaGenerator {
        @Override
        public JsonObject generate(ToolDescriptor toolDescriptor) {
            Map<String, JsonElement> root = new LinkedHashMap<>();
            root.put("type", JsonPrimitive("object"));

            // properties
            Map<String, JsonElement> props = new LinkedHashMap<>();
            for (ToolParameterDescriptor p : concat(toolDescriptor.getRequiredParameters(), toolDescriptor.getOptionalParameters())) {
                Map<String, JsonElement> prop = new LinkedHashMap<>();
                prop.put("description", JsonPrimitive(p.getDescription()));

                ToolParameterType t = p.getType();
                if (t == ToolParameterType.String.INSTANCE) {
                    prop.put("type", JsonPrimitive("string"));
                } else if (t == ToolParameterType.Integer.INSTANCE) {
                    prop.put("type", JsonPrimitive("integer"));
                } else if (t instanceof ToolParameterType.Enum) {
                    prop.put("type", JsonPrimitive("string"));
                    String[] entries = ((ToolParameterType.Enum) t).getEntries();
                    List<JsonElement> enumVals = new ArrayList<>();
                    for (String e : entries) enumVals.add(JsonPrimitive(e));
                    prop.put("enum", new JsonArray(enumVals));
                } else {
                    prop.put("type", JsonPrimitive("string")); // fallback for brevity
                }

                props.put(p.getName(), new JsonObject(prop));
            }
            root.put("properties", new JsonObject(props));

            // required array
            List<JsonElement> required = new ArrayList<>();
            for (ToolParameterDescriptor p : toolDescriptor.getRequiredParameters()) {
                required.add(JsonPrimitive(p.getName()));
            }
            root.put("required", new JsonArray(required));

            return new JsonObject(root);
        }

        private static List<ToolParameterDescriptor> concat(List<ToolParameterDescriptor> a, List<ToolParameterDescriptor> b) {
            List<ToolParameterDescriptor> res = new ArrayList<>(a.size() + b.size());
            res.addAll(a);
            res.addAll(b);
            return res;
        }
    }

    public static void main(String[] args) {
        // Build a simple tool descriptor like in the docs
        ToolDescriptor getUserTool = new ToolDescriptor(
            "get_user",
            "Returns user profile by id",
            List.of(new ToolParameterDescriptor(
                "id",
                "User id",
                ToolParameterType.String.INSTANCE
            )),
            List.of()
        );

        JsonObject schema = new MinimalSchemer().generate(getUserTool);
        // Print the schema using default JsonObject toString (compact)
        System.out.println(schema);

        // Custom schemer extending the OpenAI-compatible one is Kotlin-only in the docs; for Java example we reuse MinimalSchemer from above.
        OpenAILLMClient client = new OpenAILLMClient(System.getenv("OPENAI_API_KEY"), new OpenAIClientSettings(), null, null, new OpenAICompatibleToolDescriptorSchemaGenerator());

        Prompt prompt = Prompt.builder("p1").user("Hello").build();

        // FAILED: OpenAILLMClient.execute is a suspend function and cannot be called directly from Java without a Continuation or a dedicated non-suspending wrapper.
        // List<ai.koog.prompt.message.Message.Response> responses = client.execute(prompt, OpenAIModels.Chat.GPT4o, java.util.List.of(getUserTool));

    }
}
