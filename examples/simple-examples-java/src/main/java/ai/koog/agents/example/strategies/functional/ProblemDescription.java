package ai.koog.agents.example.strategies.functional;

import kotlinx.serialization.Serializable; /**
 * Structured description of an identified problem.
 * Returned by the first subtask so subsequent steps have typed context.
 */
@Serializable
public record ProblemDescription(String title, String details, String severity) {
}
