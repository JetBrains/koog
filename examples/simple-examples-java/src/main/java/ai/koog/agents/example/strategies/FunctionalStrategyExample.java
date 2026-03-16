package ai.koog.agents.example.strategies;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.example.ApiKeyService;
import ai.koog.prompt.executor.model.PromptExecutor;

public class FunctionalStrategyExample {
    record ProblemDescription(
        String domain,
        int complexity
    ) {}

    public static void main(String[] args) {
        var promptExecutor = PromptExecutor.builder()
            .openAI(ApiKeyService.getOpenAIApiKey())
            .build();

        var functionalAgent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .functionalStrategy("my-strategy", (ctx, userInput) -> {
                // Step 1: First, identify the problem
                // Only give the agent communication and read-only database access here
                ProblemDescription problem = ctx
                    .subtask("Identify the problem")
                    .withInput(userInput)
                    .withOutput(ProblemDescription.class)  // Type-safe output
                    .withTools(communicationTools, databaseReadTools)  // Limited tools
                    .run();

                // Step 2: Now solve the problem
                // Give the agent database write access only after problem identification
                ProblemSolution solution = ctx
                    .subtask("Solve the problem")
                    .withInput(problem)  // Use output from step 1
                    .withOutput(ProblemSolution.class)
                    .withTools(databaseReadTools, databaseWriteTools)
                    .run();

                // Verify the solution and try to fix it until the solution is satisfying
                while (true) {
                    var verificationResult = ctx
                        .subtask("Now verify that the problem is actually solved!")
                        .withInput(problemSolution)
                        .withVerification()
                        .withTools(communicationTools, databaseReadTools)
                        .run();

                    if (verificationResult.isSuccessful()) {
                        return problemSolution;
                    } else {
                        problemSolution = ctx
                            .subtask("Fix the solution based on the provided feedback:")
                            .withInput(verificationResult.getFeedback())
                            .withOutput(ProblemSolution.class)
                            .withTools(databaseReadTools, databaseWriteTools)
                            .run();
                    }
                }

            })
            .build();

    }
}
