import ai.koog.prompt.params.LLMParams;

public class ExampleLLMParameters08 {
    public static void main(String[] args) {
        // A basic set of parameters with limited length
        LLMParams basicParams = new LLMParams(
            0.7,         // temperature
            150,         // maxTokens
            1,           // numberOfChoices
            null,        // speculation
            null,        // schema
            LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
            null,        // user
            null         // additionalProperties
        );
    }
}
