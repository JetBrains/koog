import ai.koog.prompt.params.LLMParams;

public class ExampleLLMParameters06 {
    public static void main(String[] args) {
        LLMParams specificToolParams = new LLMParams(
            null,        // temperature
            null,        // maxTokens
            1,           // numberOfChoices
            null,        // speculation
            null,        // schema
            new LLMParams.ToolChoice.Named("calculator"), // toolChoice
            null,        // user
            null         // additionalProperties
        );
    }
}
