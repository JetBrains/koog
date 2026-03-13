package ai.koog.agents.example.java.api.goap;

import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.params.LLMParams;

public class Persona {
    public final String id;
    public final String name;
    public final String identity;
    public final LLModel llModel;
    public final LLMParams llmParams;
    public final double weight;

    public Persona(String id, String name, String identity, LLModel llModel, LLMParams llmParams) {
        this(id, name, identity, llModel, llmParams, 1.0);
    }

    public Persona(String id, String name, String identity, LLModel llModel, LLMParams llmParams, double weight) {
        this.id = id;
        this.name = name;
        this.identity = identity;
        this.llModel = llModel;
        this.llmParams = llmParams;
        this.weight = weight;
    }
}
