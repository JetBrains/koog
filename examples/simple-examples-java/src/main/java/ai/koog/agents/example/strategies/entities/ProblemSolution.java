package ai.koog.agents.example.strategies.entities;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured solution produced by the solving subtask.
 */
public class ProblemSolution {
    @JsonProperty("description") public final String description;

    @JsonCreator
    public ProblemSolution(
        @JsonProperty("description") String description
    ) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "ProblemSolution{\n" +
            "  description=" + description + ";\n" +
            '}';
    }
}
