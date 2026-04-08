package com.example.spring_ai_kotlin.service.deepresearch

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.planner.AIAgentPlanner
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.agents.planner.llm.PlanStep
import ai.koog.agents.planner.llm.SimplePlan
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken
import com.example.spring_ai_kotlin.service.deepresearch.model.Paper
import com.example.spring_ai_kotlin.service.deepresearch.model.ResearchState
import com.example.spring_ai_kotlin.service.deepresearch.tools.ArxivTools
import kotlinx.serialization.Serializable

private const val QUERY_TARGET = 3
private const val PAPER_TARGET = 4

fun buildResearchAgent(promptExecutor: PromptExecutor, model: LLModel): PlannerAIAgent<ResearchState, ResearchState> {
    val arxivTools = ArxivTools()

    val strategy = AIAgentPlannerStrategy(
        name = "arxiv-deep-research-planner",
        planner = ArxivResearchPlanner(arxivTools)
    )

    val config = AIAgentConfig(
        prompt = prompt("arxiv-deep-research") {
            system(
                """
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
                """.trimIndent()
            )
        },
        model = model,
        maxAgentIterations = 10
    )

    val toolRegistry = ToolRegistry {
        tools(arxivTools.asTools())
    }

    return PlannerAIAgent(
        promptExecutor = promptExecutor,
        strategy = strategy,
        agentConfig = config,
        toolRegistry = toolRegistry
    )
}

private class ArxivResearchPlanner(
    private val arxivTools: ArxivTools
) : AIAgentPlanner<ResearchState, SimplePlan>(stateType = typeToken<ResearchState>()) {
    override suspend fun buildPlan(
        context: AIAgentPlannerContext,
        state: ResearchState,
        plan: SimplePlan?
    ): SimplePlan {
        val nextStep = when {
            state.queries.size < QUERY_TARGET -> "Generate focused arXiv queries"
            state.papers.isEmpty() -> "Search arXiv for each query"
            state.selectedPapers.isEmpty() -> "Select the most relevant papers"
            state.notes.size < state.selectedPapers.size -> "Extract a note for the next selected paper"
            state.finalSummary == null -> "Write the final literature review"
            else -> "Research complete"
        }

        return SimplePlan(
            goal = "Research the topic '${state.topic}' using arXiv and produce a literature review.",
            steps = mutableListOf(PlanStep(nextStep))
        )
    }

    override suspend fun executeStep(
        context: AIAgentPlannerContext,
        state: ResearchState,
        plan: SimplePlan
    ): ResearchState {
        val currentStep = plan.steps.firstOrNull { !it.isCompleted } ?: return state

        when (currentStep.description) {
            "Generate focused arXiv queries" -> generateQueries(context, state)
            "Search arXiv for each query" -> searchPapers(state)
            "Select the most relevant papers" -> selectPapers(state)
            "Extract a note for the next selected paper" -> extractNextNote(state)
            "Write the final literature review" -> synthesizeReport(context, state)
        }

        val index = plan.steps.indexOf(currentStep)
        if (index >= 0) {
            plan.steps[index] = currentStep.copy(isCompleted = true)
        }

        return state
    }

    override suspend fun isPlanCompleted(
        context: AIAgentPlannerContext,
        state: ResearchState,
        plan: SimplePlan
    ): Boolean = state.finalSummary != null

    private suspend fun generateQueries(
        context: AIAgentPlannerContext,
        state: ResearchState
    ) {
        val response = context.llm.writeSession {
            appendPrompt {
                user(
                    """
                    Topic: ${state.topic}

                    Generate 2 to 3 arXiv search queries.
                    Make them complementary, short, and keyword-oriented.
                    Avoid punctuation-heavy natural language questions.

                    Respond with a JSON object in this exact format:
                    {"queries": ["query one", "query two", "query three"]}
                    Return only the JSON object, no other text.
                    """.trimIndent()
                )
            }

            requestLLMStructured<SearchQueries>()
        }.getOrThrow().data

        state.queries.clear()
        state.queries.addAll(
            response.queries
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(QUERY_TARGET)
        )

        if (state.queries.isEmpty()) {
            state.queries += state.topic
        }
    }

    private suspend fun searchPapers(state: ResearchState) {
        val merged = linkedMapOf<String, Paper>()

        state.queries.forEach { query ->
            arxivTools.searchArxiv(query).forEach { paper ->
                merged.putIfAbsent(paper.id, paper)
            }
        }

        state.papers.clear()
        state.papers.addAll(merged.values)
    }

    private fun selectPapers(state: ResearchState) {
        state.selectedPapers.clear()
        state.selectedPapers.addAll(state.papers.take(PAPER_TARGET))
    }

    private suspend fun extractNextNote(state: ResearchState) {
        val completedPaperIds = state.notes.map { it.paperId }.toSet()
        val nextPaper = state.selectedPapers.firstOrNull { it.id !in completedPaperIds } ?: return
        val abstract = arxivTools.getAbstract(nextPaper.id)
        val enrichedPaper = nextPaper.copy(abstract = abstract)
        val note = arxivTools.extractNote(enrichedPaper).copy(paperId = enrichedPaper.id)
        state.notes.add(note)
    }

    private suspend fun synthesizeReport(
        context: AIAgentPlannerContext,
        state: ResearchState
    ) {
        val report = context.llm.writeSession {
            appendPrompt {
                user(buildSynthesisPrompt(state))
            }

            requestLLMWithoutTools()
        }

        state.finalSummary = report.content.trim()
    }

    private fun buildSynthesisPrompt(state: ResearchState): String =
        buildString {
            appendLine("Topic: ${state.topic}")
            appendLine()
            appendLine("Selected papers:")
            state.selectedPapers.forEach { paper ->
                appendLine("- ${paper.title} (${paper.url})")
            }
            appendLine()
            appendLine("Structured notes:")
            state.notes.forEachIndexed { index, note ->
                val paper = state.selectedPapers.firstOrNull { it.id == note.paperId }
                appendLine("Paper ${index + 1}: ${paper?.title ?: note.paperId}")
                appendLine("Link: ${paper?.url ?: "https://arxiv.org/abs/${note.paperId}"}")
                appendLine("Problem: ${note.problem}")
                appendLine("Method: ${note.method}")
                appendLine("Findings: ${note.findings}")
                appendLine("Limitations: ${note.limitations}")
                appendLine()
            }
            appendLine("Return markdown with exactly this structure:")
            appendLine("# Topic")
            appendLine()
            appendLine("## Selected Papers")
            appendLine("- Title + link")
            appendLine()
            appendLine("## Paper Summaries")
            appendLine("### Paper 1")
            appendLine("- Problem")
            appendLine("- Method")
            appendLine("- Findings")
            appendLine("- Limitations")
            appendLine()
            appendLine("## Comparison")
            appendLine("- Key differences")
            appendLine("- Trends")
            appendLine()
            appendLine("## Final Takeaways")
            appendLine()
            appendLine("## Open Questions")
            appendLine()
            appendLine("Use only the provided notes.")
        }

    @Serializable
    private data class SearchQueries(
        val queries: List<String>
    )
}
