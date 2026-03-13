package ai.koog.agents.example.goap;

import kotlinx.serialization.Serializable;

@Serializable
public enum LikertRating {

    STRONGLY_DISAGREE(0.0),
    DISAGREE(0.25),
    NEUTRAL(0.5),
    AGREE(0.75),
    STRONGLY_AGREE(1.0);

    private final double score;

    LikertRating(double score) {
        this.score = score;
    }

    public double getScore() {
        return score;
    }
}

