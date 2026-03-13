package ai.koog.agents.example.java.api.goap;

import java.util.List;
import java.util.Random;

public class Creatives {
    private final List<Persona> creatives;
    private final Random random = new Random();

    public Creatives(List<Persona> creatives) {
        this.creatives = creatives;
    }

    public Persona nextCreative() {
        return creatives.get(random.nextInt(creatives.size()));
    }
}
