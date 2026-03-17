package ai.koog.agents.example.strategies.functional;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.example.ApiKeyService;
import ai.koog.agents.example.strategies.entities.ProblemDescription;
import ai.koog.agents.example.strategies.entities.ProblemSolution;
import ai.koog.agents.example.strategies.tools.*;
import ai.koog.agents.ext.agent.CriticResult;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;

public class FunctionalStrategyExample {

    public static void main(String[] args) {
        var promptExecutor = PromptExecutor.builder()
            .openAI(ApiKeyService.getOpenAIApiKey())
            .build();

        // Create tool sets
        CommunicationTools communicationTools = new CommunicationTools();
        DatabaseReadTools databaseReadTools = new DatabaseReadTools();
        DatabaseWriteTools databaseWriteTools = new DatabaseWriteTools();

        var functionalAgent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .llmModel(OpenAIModels.Chat.GPT4_1)
            .<String, ProblemSolution>functionalStrategy("my-strategy", (ctx, userInput) -> {
                // Step 1: First, identify the problem
                // Only give the agent communication and read-only database access here
                ProblemDescription problem = ctx
                    .subtask("Identify the problem: " + userInput)
                    .withInput(userInput)
                    .withOutput(ProblemDescription.class)  // Type-safe output
                    .withTools(communicationTools, databaseReadTools)  // Limited tools
                    .run();

                // Step 2: Now solve the problem
                // Give the agent database write access only after problem identification
                ProblemSolution solution = ctx
                    .subtask("Solve the problem: " + problem) // Use output from step 1
                    .withInput(problem)
                    .withOutput(ProblemSolution.class)
                    .withTools(databaseReadTools, databaseWriteTools)
                    .run();

                // Verify the solution and try to fix it until the solution is satisfying
                while (true) {
                    CriticResult<ProblemSolution> verificationResult = ctx
                        .subtask("Now verify that the problem is actually solved: " + solution)
                        .withInput(solution)
                        .withVerification()
                        .withTools(communicationTools, databaseReadTools)
                        .run();

                    if (verificationResult.isSuccessful()) {
                        return solution;
                    } else {
                        solution = ctx
                            .subtask("Fix the solution based on the provided feedback: " + verificationResult.getFeedback())
                            .withInput(verificationResult.getFeedback())
                            .withOutput(ProblemSolution.class)
                            .withTools(databaseReadTools, databaseWriteTools)
                            .run();
                    }
                }
            })
            .toolRegistry(
                ToolRegistry.builder()
                    .tools(communicationTools)
                    .tools(databaseReadTools)
                    .tools(databaseWriteTools)
                    .build()
            )
            .build();

        var result = functionalAgent.run("How to make a perfect poached egg?");

        System.out.println("\n\nAgent result:\n%s\n".formatted(result.description));
    }
}
