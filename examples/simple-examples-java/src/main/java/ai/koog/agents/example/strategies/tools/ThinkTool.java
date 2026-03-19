package ai.koog.agents.example.strategies.tools;

import ai.koog.agents.core.tools.JavaTool;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.serialization.JSONSerializer;
import ai.koog.serialization.TypeToken;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Example class-based tool implementation that mimics a simple reasoning process by allowing the LLM to think over a given thought.
 */
public class ThinkTool extends JavaTool<ThinkTool.Args, ThinkTool.Result> {
    public ThinkTool() {
        super(
            TypeToken.of(ThinkTool.Args.class),
            TypeToken.of(ThinkTool.Result.class),
            "think_tool",
            "Tool to think over a particular thought"
        );
    }

    public record Args(
        @JsonProperty("thought")
        @LLMDescription("The thought to consider")
        String thought
    ) {}

    public record Result(
        @JsonProperty("outcome")
        String outcome
    ) {}

    /**
     * Simple execute logic that bounces the thought back to the model.
     * It mimics a simple reasoning process.
     */
    @Override
    public Result execute(Args args) {
        return new Result(args.thought());
    }

    /**
     * Optional custom string result representation for the LLM.
     * By default, string representation is {@link Result} serialized to JSON using configured {@link JSONSerializer}.
     */
    @Override
    public @NotNull String encodeResultToString(Result result, @NotNull JSONSerializer serializer) {
        // Not using provided serializer, constructing string representation for the LLM manually
        return "Considered the thought:\n" + result.outcome();
    }
}
