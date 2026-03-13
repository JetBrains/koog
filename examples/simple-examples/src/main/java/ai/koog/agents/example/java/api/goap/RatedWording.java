package ai.koog.agents.example.java.api.goap;

public class RatedWording {
    public final String wording;
    public final double score;

    public RatedWording(String wording, double score) {
        this.wording = wording;
        this.score = score;
    }

    @Override
    public String toString() {
        return String.format("%s: %.2f", wording, score);
    }
}
