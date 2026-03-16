package ai.koog.agents.example.strategies;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.example.ApiKeyService;
import ai.koog.agents.planner.AIAgentPlannerStrategy;
import ai.koog.agents.planner.goap.GoapAgentState;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;

public class GoapStrategyExample {
    static class SolutionAssessment {
        boolean correct;
        String feedback;

        SolutionAssessment(boolean correct, String feedback) {
            this.correct = correct;
            this.feedback = feedback;
        }

        SolutionAssessment(String feedback) {
            this.correct = feedback.toLowerCase().contains("correct");
            this.feedback = feedback;
        }
    }

    static class MyState extends GoapAgentState<String, String> {
        public String problem;
        public String solution = null;
        public SolutionAssessment assessment = null;

        public MyState(String agentInput) {
            super(agentInput);
            problem = agentInput;
        }

        public MyState copy(String newSolution, SolutionAssessment newAssessment) {
            MyState copy = new MyState(problem);
            copy.solution = newSolution;
            copy.assessment = newAssessment;
            return copy;
        }

        @Override
        public String provideOutput() {
            return solution;
        }

        public String solveTask() {
            var task = "Solve the following problem: " + problem;
            if (solution != null && assessment != null) {
                task += "\nPrevious solution (wrong): " + solution;
                task += "\nFeedback: " + assessment.feedback;
            }
            return task;
        }

        public String assessmentTask() {
            var task = "Problem: " + problem;
            task += "\nSolution: " + solution;
            task += "\nAssess the solution";
            task += "If the solution is correct, answer 'correct'";
            task += "If the solution is incorrect, provide feedback";
            return task;
        }
    }


    public static void main(String[] args) {
        var promptExecutor = PromptExecutor.builder()
            .openAI(ApiKeyService.getOpenAIApiKey())
            .build();

        var strategy = AIAgentPlannerStrategy.builder("my-strategy")
            .goap(MyState::new)
            .goal("solve the task", builder ->
                builder.condition(state -> state.assessment != null && state.assessment.correct)
            )
            .action(
                "solve", builder ->
                    builder
                        .precondition(state -> true) // always can execute
                        .belief(state -> state.copy("solution", null))
                        .execute((context, state) -> {
                                var solution = context.requestLLM(state.solveTask()).getContent();
                                return state.copy(solution, null);
                            }
                        )
            )
            .action(
                "verify", builder ->
                    builder
                        .precondition(state -> state.solution != null && state.assessment == null)
                        .belief(state -> state.copy(state.solution, new SolutionAssessment(true, "solved")))
                        .execute((context, state) -> {
                                var feedback = context.requestLLM(state.assessmentTask()).getContent();
                                return state.copy(state.solution, new SolutionAssessment(feedback));
                            }
                        )
            )
            .build();

        var agent = AIAgent.builder()
            .plannerStrategy(strategy)
            .promptExecutor(promptExecutor)
            .llmModel(OpenAIModels.Chat.GPT4o)
            .build();

        var result = agent.run("Solve the following problem: Find the square root of 16");

        System.out.println(result);
    }
}
