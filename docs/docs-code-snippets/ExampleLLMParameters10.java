import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams;
import ai.koog.prompt.executor.clients.openai.models.OpenAIInclude;

import java.util.Arrays;

public class ExampleLLMParameters10 {
    public static void main(String[] args) {
        OpenAIResponsesParams openAIStatelessReasoningParams = new OpenAIResponsesParams(
            null,        // temperature
            null,        // maxTokens
            1,           // numberOfChoices
            null,        // speculation
            null,        // schema
            null,        // toolChoice
            null,        // user
            null,        // additionalProperties
            null,        // background
            Arrays.asList(OpenAIInclude.REASONING_ENCRYPTED_CONTENT), // include
            null,        // logprobs
            null,        // maxToolCalls
            null,        // parallelToolCalls
            null,        // promptCacheKey
            null,        // reasoning
            null,        // safetyIdentifier
            null,        // serviceTier
            null,        // store
            null,        // topLogprobs
            null,        // topP
            null         // truncation
        );
    }
}
