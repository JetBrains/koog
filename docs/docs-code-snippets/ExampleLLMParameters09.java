import ai.koog.prompt.executor.clients.openai.OpenAIChatParams;
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort;

public class ExampleLLMParameters09 {
    public static void main(String[] args) {
        OpenAIChatParams openAIReasoningEffortParams = new OpenAIChatParams(
            null,        // temperature
            null,        // maxTokens
            1,           // numberOfChoices
            null,        // speculation
            null,        // schema
            null,        // toolChoice
            null,        // user
            null,        // additionalProperties
            null,        // audio
            null,        // frequencyPenalty
            null,        // logprobs
            null,        // parallelToolCalls
            null,        // presencePenalty
            null,        // promptCacheKey
            ReasoningEffort.MEDIUM, // reasoningEffort
            null,        // safetyIdentifier
            null,        // serviceTier
            null,        // stop
            null,        // store
            null,        // topLogprobs
            null,        // topP
            null         // webSearchOptions
        );
    }
}
