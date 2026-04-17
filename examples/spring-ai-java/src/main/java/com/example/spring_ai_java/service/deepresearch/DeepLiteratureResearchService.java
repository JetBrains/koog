package com.example.spring_ai_java.service.deepresearch;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.planner.AIAgentPlannerStrategy;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;
import com.example.spring_ai_java.service.deepresearch.model.ResearchState;
import org.springframework.stereotype.Service;

@Service
public class DeepLiteratureResearchService {

    private final PromptExecutor promptExecutor;

    public DeepLiteratureResearchService(PromptExecutor promptExecutor) {
        this.promptExecutor = promptExecutor;
    }

    public String createAndRunAgent(String userPrompt) {
        var arxivTools = new ArxivTools();

        var strategy = AIAgentPlannerStrategy.builder("arxiv-deep-research-planner")
            .withPlanner(new ArxivResearchPlanner(arxivTools))
            .withInput(ResearchState::new)
            .withOutput(ResearchState::getFinalSummary)
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .systemPrompt("""
                You are an arXiv deep research planner.
                Follow this workflow:
                1. Generate 2 to 3 focused search queries for the topic.
                2. Search arXiv.
                3. Select the 3 to 5 most relevant papers.
                4. Read abstracts and extract one structured note per selected paper.
                5. Stop when the selected set gives enough coverage.
                6. Produce a final markdown literature review.

                Favor breadth across approaches over near-duplicate papers.
                Use only gathered evidence. Keep outputs concise and production-realistic.
                """)
            .llmModel(OpenAIModels.Chat.GPT5Nano)
            .toolRegistry(ToolRegistry.builder().tools(arxivTools).build())
            .plannerStrategy(strategy)
            .maxIterations(10)
            .build();

        return (String) agent.run(userPrompt);
    }
}
