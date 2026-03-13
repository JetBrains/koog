package ai.koog.agents.example.goap;

import kotlinx.serialization.Serializable;

import java.util.List;

@Serializable
public record Reaction(
    String feedback,
    List<LikertRating> ratings
) {
    public Reaction() {
        this("", List.of());
    }
}
