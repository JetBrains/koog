package ai.koog.agents.example.strategies.entities;


import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured solution produced by the solving subtask.
 */
public record ProblemSolution(
    @JsonProperty("description") String description
) {}
