import ai.koog.prompt.executor.clients.openrouter.OpenRouterParams;

import java.util.Arrays;

public class ExampleLLMParameters07 {
    public static void main(String[] args) {
        OpenRouterParams openRouterParams = new OpenRouterParams(
            0.7,         // temperature
            500,         // maxTokens
            1,           // numberOfChoices
            null,        // speculation
            null,        // schema
            null,        // toolChoice
            null,        // user
            null,        // additionalProperties
            0.5,         // frequencyPenalty
            null,        // logprobs
            null,        // minP
            Arrays.asList("anthropic/claude-3-opus", "anthropic/claude-3-sonnet"), // models
            0.5,         // presencePenalty
            null,        // provider
            1.1,         // repetitionPenalty
            null,        // route
            null,        // stop
            null,        // topA
            40,          // topK
            null,        // topLogprobs
            0.9,         // topP
            Arrays.asList("middle-out") // transforms
        );
    }
}
