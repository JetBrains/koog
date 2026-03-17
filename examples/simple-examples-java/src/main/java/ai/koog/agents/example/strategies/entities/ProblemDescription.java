package ai.koog.agents.example.strategies.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured description of an identified problem.
 * Returned by the first subtask so subsequent steps have typed context.
 */
public class ProblemDescription {
    @JsonProperty("title") public final String title;
    @JsonProperty("details") public final String details;
    @JsonProperty("severity") public final String severity;

    @JsonCreator
    public ProblemDescription(
        @JsonProperty("title") String title,
        @JsonProperty("details") String details,
        @JsonProperty("severity") String severity
    ) {
        this.title = title;
        this.details = details;
        this.severity = severity;
    }

    @Override
    public String toString() {
        return "ProblemDescription{\n" +
            "  title=" + title + ";\n" +
            "  details=" + details + ";\n" +
            "  severity=" + severity + ";\n" +
            '}';
    }
}
