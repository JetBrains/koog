package ai.koog.agents.example.goap;

import kotlinx.serialization.Serializable;

import java.util.List;

@Serializable
public record Proposal(
    String learnings,
    List<String> wordings
) {
    public Proposal() {
        this("", List.of());
    }
}
