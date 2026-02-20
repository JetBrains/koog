import ai.koog.prompt.params.LLMParams;
import ai.koog.prompt.params.AdditionalPropertiesKt;

public class ExampleLLMParameters11 {
    public static void main(String[] args) {
        // Add custom parameters for specific model providers
        LLMParams customParams = new LLMParams(
            null,        // temperature
            null,        // maxTokens
            1,           // numberOfChoices
            null,        // speculation
            null,        // schema
            null,        // toolChoice
            null,        // user
            AdditionalPropertiesKt.additionalPropertiesOf(
                "top_p", 0.95,
                "frequency_penalty", 0.5,
                "presence_penalty", 0.5
            )
        );
    }
}
