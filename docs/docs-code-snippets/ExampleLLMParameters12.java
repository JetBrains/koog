import ai.koog.prompt.params.LLMParams;

public class ExampleLLMParameters12 {
    public static void main(String[] args) {
        // Define default parameters
        LLMParams defaultParams = new LLMParams(
            0.7,         // temperature
            150,         // maxTokens
            1,           // numberOfChoices
            null,        // speculation
            null,        // schema
            LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
            null,        // user
            null         // additionalProperties
        );

        // Create parameters with some overrides, using defaults for the rest
        LLMParams overrideParams = new LLMParams(
            0.2,         // temperature
            null,        // maxTokens
            3,           // numberOfChoices
            null,        // speculation
            null,        // schema
            null,        // toolChoice
            null,        // user
            null         // additionalProperties
        ).applyDefaults(defaultParams);
    }
}
