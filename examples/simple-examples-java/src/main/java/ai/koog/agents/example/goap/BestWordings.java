package ai.koog.agents.example.goap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BestWordings {
    public final List<RatedWording> wordings;

    public BestWordings() {
        this.wordings = List.of();
    }

    public BestWordings(List<RatedWording> wordings) {
        this.wordings = wordings;
    }

    public BestWordings add(List<RatedWording> newWordings, int maxWordingsToStore) {
        List<RatedWording> combined = new ArrayList<>(wordings);
        combined.addAll(newWordings);
        combined.sort(Comparator.comparingDouble((RatedWording w) -> w.score).reversed());
        return new BestWordings(combined.subList(0, Math.min(combined.size(), maxWordingsToStore)));
    }

    public List<RatedWording> best(double minScore) {
        return wordings.stream().filter(w -> w.score >= minScore).collect(Collectors.toList());
    }

    public String show(int n) {
        return wordings.subList(0, Math.min(n, wordings.size())).stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
    }
}
