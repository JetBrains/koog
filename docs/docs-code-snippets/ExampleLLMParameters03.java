import ai.koog.prompt.params.LLMParams;

public class ExampleLLMParameters03 {
    public static void main(String[] args) {
        // FAILED: The `llm.writeSession` is a Kotlin DSL construct that operates within a suspend context.
        // There is no direct Java equivalent for `writeSession` or `changeLLMParams` in the current public API.
        // You would need to use the underlying session management APIs directly, which may not be exposed for Java.
        /*
        llm.writeSession(session -> {
            session.changeLLMParams(new LLMParams(
                0.7,         // temperature
                500,         // maxTokens
                1,           // numberOfChoices
                null,        // speculation
                null,        // schema
                LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
                null,        // user
                null         // additionalProperties
            ));
        });
        */
    }
}
