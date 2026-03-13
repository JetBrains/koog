package ai.koog.agents.example.goap;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FocusGroup {
    public final List<Persona> participants;
    private final List<Double> weights;

    public FocusGroup(List<Persona> participants) {
        this.participants = participants;
        double totalWeight = participants.stream().mapToDouble(p -> p.weight).sum();
        this.weights = participants.stream()
                .map(p -> p.weight / totalWeight)
                .collect(Collectors.toList());
    }

    public double score(List<LikertRating> ratings) {
        double total = 0.0;
        for (int i = 0; i < weights.size() && i < ratings.size(); i++) {
            total += weights.get(i) * ratings.get(i).getScore();
        }
        return total;
    }

    public List<String> presentFeedback(List<Reaction> reactions) {
        List<String> feedback = new ArrayList<>();
        for (int i = 0; i < participants.size() && i < reactions.size(); i++) {
            feedback.add(participants.get(i).name + ": " + reactions.get(i).feedback());
        }
        return feedback;
    }
}
