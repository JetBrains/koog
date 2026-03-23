package ai.koog.agents.core.tools;

import ai.koog.serialization.TypeToken;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Implementation of the {@link JavaTool} for test.
 */
public class ThinkJavaTool extends JavaTool<ThinkJavaTool.Args, ThinkJavaTool.Result> {
    public ThinkJavaTool() {
        super(
            TypeToken.of(ThinkJavaTool.Args.class),
            TypeToken.of(ThinkJavaTool.Result.class),
            "think_tool",
            "Tool to think over a particular thought"
        );
    }

    static class Args {
        @JsonProperty("thought") public final String thought;

        @JsonCreator
        public Args(
            @JsonProperty("thought")
            String thought
        ) {
            this.thought = thought;
        }
    }

    static class Result {
        @JsonProperty("outcome") public final String outcome;

        @JsonCreator
        public Result(
            @JsonProperty("outcome") String outcome
        ) {
            this.outcome = outcome;
        }
    }

    /**
     * Simple execute logic that bounces the thought back to the model.
     * It mimics a simple reasoning process.
     */
    @Override
    public Result execute(Args args) {
        return new Result(args.thought);
    }
}
