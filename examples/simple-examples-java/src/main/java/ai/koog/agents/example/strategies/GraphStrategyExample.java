package ai.koog.agents.example.strategies;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.entity.AIAgentEdge;
import ai.koog.agents.core.agent.entity.AIAgentNode;
import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
import ai.koog.agents.example.ApiKeyService;
import ai.koog.agents.ext.agent.CriticResult;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;

import java.util.Collections;

public class GraphStrategyExample {
    /**
     * Structured description of an identified problem.
     * Returned by the first subtask so subsequent steps have typed context.
     */
    public record ProblemDescription(String title, String details, String severity) {
    }

    /**
     * Structured solution produced by the solving subtask.
     */
    public record ProblemSolution(String description) {
    }


    public static void main(String[] args) {
        List<? extends Tool<?, ?>> availableTools = Collections.emptyList();

        var promptExecutor = PromptExecutor.builder()
            .openAI(ApiKeyService.getOpenAIApiKey())
            .build();

        var graphAgent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .llmModel(OpenAIModels.Chat.GPT4_1)
            .graphStrategy("problem-solver", builder -> {
                // Define the overall graph structure with typed input/output
                var graph = builder
                    .withInput(String.class)
                    .withOutput(ProblemSolution.class);

                // Step 1: Identify the problem using an LLM subgraph with limited tools
                var identifyProblem = AIAgentSubgraph.builder("identify-problem")
                    .withInput(String.class)
                    .withOutput(ProblemDescription.class)
                    .limitedTools(availableTools)
                    .withTask(input -> "Analyze the following and identify the core problem: " + input)
                    .build();

                // Step 2: Solve the problem using another LLM subgraph
                var solveProblem = AIAgentSubgraph.builder("solve-problem")
                    .withInput(ProblemDescription.class)
                    .withOutput(ProblemSolution.class)
                    .limitedTools(availableTools)
                    .withTask(problem -> "Propose a solution for: " + problem.title() + " - " + problem.details())
                    .build();

                // Step 3: Verify the solution using LLM-as-a-judge
                var verifySolution = AIAgentNode.builder("verify-solution")
                    .withInput(ProblemSolution.class)
                    .llmAsAJudge("Verify whether the proposed solution is correct and complete");

                // Step 4: Fix the solution based on feedback
                var fixSolution = AIAgentSubgraph.builder("fix-solution")
                    .withInput(String.class)
                    .withOutput(ProblemSolution.class)
                    .limitedTools(availableTools)
                    .withTask(feedback -> "Fix the solution based on this feedback: " + feedback)
                    .build();

                // Connect the nodes with edges to define execution flow
                graph.edge(graph.nodeStart, identifyProblem);
                graph.edge(identifyProblem, solveProblem);
                graph.edge(solveProblem, verifySolution);

                // Conditional edges: if verification succeeds, finish; otherwise, attempt a fix
                graph.edge(AIAgentEdge.builder()
                    .from(verifySolution)
                    .to(graph.nodeFinish)
                    .onCondition(CriticResult::isSuccessful)
                    .transformed(result -> (ProblemSolution) result.getInput())
                    .build());

                graph.edge(AIAgentEdge.builder()
                    .from(verifySolution)
                    .to(fixSolution)
                    .onCondition(verification -> !verification.isSuccessful())
                    .transformed(CriticResult::getFeedback)
                    .build());

                graph.edge(fixSolution, verifySolution);

                return graph.build();
            })
            .build();


        System.out.println(graphAgent.run("Fix the compilation error in the file MyService.java"));
    }
}
