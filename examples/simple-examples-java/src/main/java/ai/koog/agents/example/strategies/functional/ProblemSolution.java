package ai.koog.agents.example.strategies.functional;

import kotlinx.serialization.Serializable; /**
 * Structured solution produced by the solving subtask.
 * Contains the fix description and steps taken.
 */
@Serializable
public record ProblemSolution(String description, String stepsTaken) {
}
