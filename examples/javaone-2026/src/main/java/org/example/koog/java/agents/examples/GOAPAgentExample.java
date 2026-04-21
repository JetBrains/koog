package org.example.koog.java.agents.examples;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.ext.agent.CriticResult;
import ai.koog.agents.planner.AIAgentPlannerStrategy;
import ai.koog.agents.planner.goap.GoapAgentState;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import org.example.koog.java.structs.AccountIssueSolution;
import org.example.koog.java.structs.AccountIssueSummary;

// Unused example, shows how to build GOAP agents:
public class GOAPAgentExample {

    public static class MyAgentState extends GoapAgentState<String, AccountIssueSolution> {
        public AccountIssueSummary summary = null;
        public AccountIssueSolution solution = null;
        public CriticResult<AccountIssueSolution> verification = null;

        public MyAgentState(String agentInput) {
            super(agentInput);
        }

        private static MyAgentState create(String agentInput, AccountIssueSummary summary, AccountIssueSolution solution, CriticResult<AccountIssueSolution> verification) {
            var state = new MyAgentState(agentInput);
            state.summary = summary;
            state.solution = solution;
            state.verification = verification;
            return state;
        }

        public MyAgentState withSummary(AccountIssueSummary summary) {
            return create(getAgentInput(), summary, solution, verification);
        }

        public MyAgentState withSolution(AccountIssueSolution solution) {
            return create(getAgentInput(), summary, solution, verification);
        }

        public MyAgentState withVerification(CriticResult<AccountIssueSolution> verification) {
            return create(getAgentInput(), summary, solution, verification);
        }

        @Override
        public AccountIssueSolution provideOutput() {
            return solution;
        }
    }

    private void defineAgent(ToolSet conversationTools, ToolSet databaseReadTools, ToolSet databaseWriteTools) {

        var plannerAgent = AIAgent.builder()
                .plannerStrategy(AIAgentPlannerStrategy.builder("name")
                        .goap(MyAgentState::new)
                        .action("identify-problem", builder -> builder
                                .precondition(state -> state.summary == null)
                                .belief(state -> state.withSummary(AccountIssueSummary.some()))
                                .execute((ctx, state) ->
                                        state.withSummary(ctx
                                                .subtask("Identify the user's problem and provide a summary")
                                                .withInput(state.getAgentInput())
                                                .withOutput(AccountIssueSummary.class)
                                                .withTools(conversationTools, databaseReadTools)
                                                .useLLM(OpenAIModels.Chat.GPT4o)
                                                .run()
                                        )
                                )
                        )
                        .action("solve", builder -> builder
                                .precondition(state -> state.summary != null && state.solution == null)
                                .belief(state -> state.withSolution(AccountIssueSolution.empty()))
                                .execute((ctx, state) -> {
                                    var solution = ctx
                                            .subtask("Solve the user's problem")
                                            .withInput(state.summary)
                                            .withOutput(AccountIssueSolution.class)
                                            .withTools(databaseReadTools, databaseWriteTools)
                                            .useLLM(AnthropicModels.Sonnet_4_5)
                                            .run();

                                    return state.withSolution(solution);
                                })
                        )
                        .action("verify", builder -> builder
                                .precondition(state -> state.solution != null && state.verification == null)
                                .belief(state -> state.withVerification(new CriticResult<>(true, "", state.solution)))
                                .execute((ctx, state) -> {
                                    var verification = ctx
                                            .subtask("Verify that the original user's problem with bank account is solved")
                                            .withInput(state.solution)
                                            .withVerification()
                                            .useLLM(OpenAIModels.Chat.O3)
                                            .withTools(conversationTools, databaseReadTools)
                                            .run();

                                    return state.withVerification(verification);
                                })
                        )
                        .action("adjust", builder -> builder
                                .precondition(state -> state.verification != null && !state.verification.isSuccessful())
                                .belief(state -> state.withVerification(null))
                                .execute((ctx, state) -> {
                                    var newSolution = ctx
                                            .subtask("Refine the solution according to the provided feedback.")
                                            .withInput(state.verification.getFeedback())
                                            .withOutput(AccountIssueSolution.class)
                                            .useLLM(OpenAIModels.Chat.GPT5_2Pro)
                                            .withTools(databaseReadTools, databaseWriteTools)
                                            .run();

                                    return state.withSolution(newSolution);
                                })
                        )
                        .goal("solved-and-verified", builder ->
                                builder.condition(state ->
                                        state.verification != null && state.verification.isSuccessful()
                                )
                        )
                        .build()
                )
                .toolRegistry(
                        ToolRegistry.builder()
                                .tools(conversationTools)
                                .tools(databaseReadTools)
                                .tools(databaseWriteTools)
                                .build()
                )
                .agentConfig(
                        AIAgentConfig.builder()
                                .model(OpenAIModels.Chat.GPT5)
                                .prompt(
                                        Prompt.builder("initial-prompt")
                                                .system("You are a helpful banking assistant with access to banking tools. Your goal is to help the user with their bank account and transactions")
                                                .build()
                                )
                                .build()
                )
                .build();

        plannerAgent.run("Cars and motorsport");
    }
}
