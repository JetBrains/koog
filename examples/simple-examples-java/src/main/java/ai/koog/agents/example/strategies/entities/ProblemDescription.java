package ai.koog.agents.example.strategies.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured description of an identified problem.
 * Returned by the first subtask so subsequent steps have typed context.
 */
public record ProblemDescription(
    @JsonProperty("title") String title,
    @JsonProperty("details") String details,
    @JsonProperty("severity") String severity
) {}
