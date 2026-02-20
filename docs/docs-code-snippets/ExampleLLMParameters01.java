import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.params.LLMParams;

public class ExampleLLMParameters01 {
    public static void main(String[] args) {
        Prompt prompt = Prompt.builder("dev-assistant")
            .withParams(new LLMParams(
                0.7,         // temperature
                500,         // maxTokens
                1,           // numberOfChoices
                null,        // speculation
                null,        // schema
                LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
                null,        // user
                null         // additionalProperties
            ))
            .system("You are a helpful assistant.")
            .user("Tell me about Kotlin")
            .build();
    }
}
