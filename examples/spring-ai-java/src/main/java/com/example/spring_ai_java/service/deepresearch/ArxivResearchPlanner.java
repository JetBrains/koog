package com.example.spring_ai_java.service.deepresearch;

import ai.koog.agents.core.agent.context.AIAgentPlannerContext;
import ai.koog.agents.planner.JavaAIAgentPlanner;
import ai.koog.agents.planner.llm.PlanStep;
import ai.koog.agents.planner.llm.SimplePlan;
import com.example.spring_ai_java.service.deepresearch.model.Note;
import com.example.spring_ai_java.service.deepresearch.model.Paper;
import com.example.spring_ai_java.service.deepresearch.model.ResearchState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ArxivResearchPlanner extends JavaAIAgentPlanner<ResearchState, SimplePlan> {

    private static final int QUERY_TARGET = 3;
    private static final int PAPER_TARGET = 4;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ArxivTools arxivTools;

    public ArxivResearchPlanner(ArxivTools arxivTools) {
        this.arxivTools = arxivTools;
    }

    @Override
    protected SimplePlan buildPlan(AIAgentPlannerContext context, ResearchState state, @Nullable SimplePlan plan) {
        String nextStep;
        if (state.getQueries().size() < QUERY_TARGET) {
            nextStep = "Generate focused arXiv queries";
        } else if (state.getPapers().isEmpty()) {
            nextStep = "Search arXiv for each query";
        } else if (state.getSelectedPapers().isEmpty()) {
            nextStep = "Select the most relevant papers";
        } else if (state.getNotes().size() < state.getSelectedPapers().size()) {
            nextStep = "Extract a note for the next selected paper";
        } else if (state.getFinalSummary() == null) {
            nextStep = "Write the final literature review";
        } else {
            nextStep = "Research complete";
        }

        return new SimplePlan(
            "Research the topic '" + state.getTopic() + "' using arXiv and produce a literature review.",
            new ArrayList<>(List.of(new PlanStep(nextStep, false)))
        );
    }

    @Override
    protected ResearchState executeStep(AIAgentPlannerContext context, ResearchState state, SimplePlan plan) {
        PlanStep currentStep = plan.getSteps().stream()
            .filter(s -> !s.isCompleted())
            .findFirst()
            .orElse(null);

        if (currentStep == null) return state;

        switch (currentStep.getDescription()) {
            case "Generate focused arXiv queries" -> generateQueries(context, state);
            case "Search arXiv for each query" -> searchPapers(state);
            case "Select the most relevant papers" -> selectPapers(state);
            case "Extract a note for the next selected paper" -> extractNextNote(state);
            case "Write the final literature review" -> synthesizeReport(context, state);
        }

        int index = plan.getSteps().indexOf(currentStep);
        if (index >= 0) {
            plan.getSteps().set(index, new PlanStep(currentStep.getDescription(), true));
        }

        return state;
    }

    @Override
    protected Boolean isPlanCompleted(AIAgentPlannerContext context, ResearchState state, SimplePlan plan) {
        return state.getFinalSummary() != null;
    }

    private void generateQueries(AIAgentPlannerContext context, ResearchState state) {
        String responseText = context.getLlm().writeSession(session -> {
            session.appendPrompt(prompt -> {
                prompt.user("""
                    Topic: %s
                    
                    Generate 2 to 3 arXiv search queries.
                    Make them complementary, short, and keyword-oriented.
                    Avoid punctuation-heavy natural language questions.
                    
                    Respond with a JSON object in this exact format:
                    {"queries": ["query one", "query two", "query three"]}
                    Return only the JSON object, no other text.
                    ""\".formatted(state.getTopic())
                    
                    """);
                return null;
            });
            return session.requestLLMWithoutTools().getContent();
        });

        try {
            var node = objectMapper.readTree(responseText);
            var queriesNode = node.get("queries");
            if (queriesNode != null && queriesNode.isArray()) {
                state.getQueries().clear();
                for (var q : queriesNode) {
                    var query = q.asText().trim();
                    if (!query.isBlank()) {
                        state.getQueries().add(query);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (state.getQueries().isEmpty()) {
            state.getQueries().add(state.getTopic());
        }

        var distinct = state.getQueries().stream().distinct().limit(QUERY_TARGET).toList();
        state.getQueries().clear();
        state.getQueries().addAll(distinct);
    }

    private void searchPapers(ResearchState state) {
        var merged = new LinkedHashMap<String, Paper>();
        for (var query : state.getQueries()) {
            for (var paper : arxivTools.searchArxiv(query)) {
                merged.putIfAbsent(paper.getId(), paper);
            }
        }
        state.getPapers().clear();
        state.getPapers().addAll(merged.values());
    }

    private void selectPapers(ResearchState state) {
        state.getSelectedPapers().clear();
        state.getSelectedPapers().addAll(
            state.getPapers().stream().limit(PAPER_TARGET).toList()
        );
    }

    private void extractNextNote(ResearchState state) {
        var completedPaperIds = state.getNotes().stream()
            .map(Note::getPaperId)
            .collect(Collectors.toSet());

        var nextPaper = state.getSelectedPapers().stream()
            .filter(p -> !completedPaperIds.contains(p.getId()))
            .findFirst()
            .orElse(null);

        if (nextPaper == null) return;

        var abstractText = arxivTools.getAbstract(nextPaper.getId());
        var enrichedPaper = new Paper(
            nextPaper.getId(), nextPaper.getTitle(), nextPaper.getAuthors(),
            abstractText, nextPaper.getUrl(), nextPaper.getPublished()
        );
        var note = arxivTools.extractNote(enrichedPaper);
        state.getNotes().add(note);
    }

    private void synthesizeReport(AIAgentPlannerContext context, ResearchState state) {
        var report = context.getLlm().writeSession(session -> {
            session.appendPrompt(prompt -> {
                prompt.user(buildSynthesisPrompt(state));
                return null;
            });
            return session.requestLLMWithoutTools();
        });
        state.setFinalSummary(report.getContent().trim());
    }

    private String buildSynthesisPrompt(ResearchState state) {
        var sb = new StringBuilder();
        sb.append("Topic: ").append(state.getTopic()).append("\n\n");
        sb.append("Selected papers:\n");
        for (var paper : state.getSelectedPapers()) {
            sb.append("- ").append(paper.getTitle()).append(" (").append(paper.getUrl()).append(")\n");
        }
        sb.append("\nStructured notes:\n");
        for (int i = 0; i < state.getNotes().size(); i++) {
            var note = state.getNotes().get(i);
            var paper = state.getSelectedPapers().stream()
                .filter(p -> p.getId().equals(note.getPaperId()))
                .findFirst().orElse(null);
            sb.append("Paper ").append(i + 1).append(": ")
                .append(paper != null ? paper.getTitle() : note.getPaperId()).append("\n");
            sb.append("Link: ")
                .append(paper != null ? paper.getUrl() : "https://arxiv.org/abs/" + note.getPaperId()).append("\n");
            sb.append("Problem: ").append(note.getProblem()).append("\n");
            sb.append("Method: ").append(note.getMethod()).append("\n");
            sb.append("Findings: ").append(note.getFindings()).append("\n");
            sb.append("Limitations: ").append(note.getLimitations()).append("\n\n");
        }
        sb.append("""
            Return markdown with exactly this structure:
            # Topic
            
            ## Selected Papers
            - Title + link
            
            ## Paper Summaries
            ### Paper 1
            - Problem
            - Method
            - Findings
            - Limitations
            
            ## Comparison
            - Key differences
            - Trends
            
            ## Final Takeaways
            
            ## Open Questions
            
            Use only the provided notes.""");
        return sb.toString();
    }
}
