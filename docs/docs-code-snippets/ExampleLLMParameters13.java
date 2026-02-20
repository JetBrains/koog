import ai.koog.prompt.params.LLMParams;

public class ExampleLLMParameters13 {
    public static void main(String[] args) {
        LLMParams overrideParams = new LLMParams(
            0.2,         // temperature
            150,         // maxTokens
            3,           // numberOfChoices
            null,        // speculation
            null,        // schema
            LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
            null,        // user
            null         // additionalProperties
        );
    }
}
