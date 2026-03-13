package ai.koog.agents.example.goap;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.planner.AIAgentPlannerStrategy;
import ai.koog.agents.planner.goap.GoapAgentState;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.params.LLMParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A GOAP-based agent that iteratively generates and evaluates message wordings
 * using a focus group of AI personas.
 *
 * <p>Requires OPENAI_API_KEY and ANTHROPIC_API_KEY environment variables.
 */
public class GrouperAgent {

    static class GrouperState extends GoapAgentState<GrouperConfig, String> {
        public final GrouperConfig config;
        public final BestWordings bestWordings;
        public final int iteration;
        public final List<String> newWordings;
        public final List<String> feedback;
        public final List<String> learnings;

        GrouperState(GrouperConfig config) {
            super(config);
            this.config = config;
            this.bestWordings = new BestWordings();
            this.iteration = 0;
            this.newWordings = List.of();
            this.feedback = List.of();
            this.learnings = List.of();
        }

        private GrouperState(GrouperConfig config, BestWordings bestWordings, int iteration,
                             List<String> newWordings, List<String> feedback, List<String> learnings) {
            super(config);
            this.config = config;
            this.bestWordings = bestWordings;
            this.iteration = iteration;
            this.newWordings = newWordings;
            this.feedback = feedback;
            this.learnings = learnings;
        }

        GrouperState withBestWordings(BestWordings bestWordings) {
            return new GrouperState(config, bestWordings, iteration, newWordings, feedback, learnings);
        }

        GrouperState withIteration(int iteration) {
            return new GrouperState(config, bestWordings, iteration, newWordings, feedback, learnings);
        }

        GrouperState withNewWordings(List<String> newWordings) {
            return new GrouperState(config, bestWordings, iteration, newWordings, feedback, learnings);
        }

        GrouperState withFeedback(List<String> feedback) {
            return new GrouperState(config, bestWordings, iteration, newWordings, feedback, learnings);
        }

        GrouperState withLearnings(List<String> learnings) {
            return new GrouperState(config, bestWordings, iteration, newWordings, feedback, learnings);
        }

        @Override
        public String provideOutput() {
            return bestWordings.show(config.numWordingsRequired);
        }
    }

    public static void main(String[] args) {
        String openAIApiKey = System.getenv("OPENAI_API_KEY");
        if (openAIApiKey == null || openAIApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
        }

        String anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
        }

        // Focus group participants
        Persona participant1 = new Persona(
                "participant1", "Alex",
                "A 25-year-old urban professional who values directness and clarity",
                OpenAIModels.Chat.GPT4o,
                new LLMParams(Double.valueOf(0.7), null, null, null, null, null, null, null)
        );
        Persona participant2 = new Persona(
                "participant2", "Jordan",
                "A 40-year-old parent concerned about health issues affecting youth",
                OpenAIModels.Chat.GPT4_1,
                new LLMParams(Double.valueOf(0.3), null, null, null, null, null, null, null)
        );
        Persona participant3 = new Persona(
                "participant3", "Taylor",
                "A 19-year-old college student who responds to emotional appeals",
                AnthropicModels.Opus_4_6,
                new LLMParams(Double.valueOf(1.0), null, null, null, null, null, null, null)
        );

        // Creatives
        Persona creative1 = new Persona(
                "creative1", "Morgan",
                "An advertising professional specializing in impactful public health campaigns",
                OpenAIModels.Chat.GPT4o,
                new LLMParams(Double.valueOf(1.2), null, null, null, null, null, null, null)
        );
        Persona creative2 = new Persona(
                "creative2", "Casey",
                "A copywriter with experience in creating concise, memorable slogans",
                OpenAIModels.Chat.GPT4_1,
                new LLMParams(Double.valueOf(0.5), null, null, null, null, null, null, null)
        );
        Persona creative3 = new Persona(
                "creative3", "Riley",
                "A behavioral psychologist who understands persuasive messaging techniques",
                AnthropicModels.Opus_4_6,
                new LLMParams(Double.valueOf(0.8), null, null, null, null, null, null, null)
        );

        Message message = new Message("smoking", "smoking is bad", "deter smoking", "billboard slogan");
        GrouperConfig config = new GrouperConfig(
                new FocusGroup(Arrays.asList(participant1, participant2, participant3)),
                new Creatives(Arrays.asList(creative1, creative2, creative3)),
                message
        );

        var strategy = AIAgentPlannerStrategy.builder("grouper-strategy")
                .goap((GrouperConfig cfg) -> new GrouperState(cfg))
                .action("evolve-message-wording", builder -> builder
                        .precondition(state -> state.newWordings.isEmpty())
                        .belief(state -> {
                            List<String> defaultWordings = IntStream.range(0, state.config.numProposals)
                                    .mapToObj(i -> "")
                                    .collect(Collectors.toList());
                            List<String> newLearnings = new ArrayList<>(state.learnings);
                            newLearnings.add("");
                            return state
                                    .withNewWordings(defaultWordings)
                                    .withLearnings(newLearnings)
                                    .withIteration(state.iteration + 1);
                        })
                        .execute((ctx, state) -> {
                            Persona creative = state.config.creatives.nextCreative();
                            String input = "OBJECTIVE: " + state.config.message.objective +
                                    "\nDELIVERABLE: " + state.config.message.deliverable +
                                    "\n\nPrevious feedback:\n" + formatIndexed(state.feedback) +
                                    "\n\nPrevious learnings:\n" + formatIndexed(state.learnings) +
                                    "\n\nCreate up to " + state.config.numProposals + " new message variations." +
                                    "\n\nCurrent top performing messages:\n" + state.bestWordings.show(state.config.numWordingsToShow);

                            Proposal proposal = ctx.subtask("Generate creative message variations as " + creative.name)
                                    .withInput(input)
                                    .withOutput(Proposal.class)
                                    .useLLM(creative.llModel)
                                    .run();

                            List<String> newLearnings = new ArrayList<>(state.learnings);
                            newLearnings.add(proposal.learnings());
                            return state
                                    .withNewWordings(proposal.wordings())
                                    .withLearnings(newLearnings)
                                    .withIteration(state.iteration + 1);
                        })
                )
                .action("run-focus-group", builder -> builder
                        .precondition(state -> !state.newWordings.isEmpty())
                        .belief(state -> {
                            List<RatedWording> optimisticRatings = state.newWordings.stream()
                                    .map(w -> new RatedWording(w, 1.0))
                                    .collect(Collectors.toList());
                            return state
                                    .withNewWordings(Collections.emptyList())
                                    .withBestWordings(state.bestWordings.add(optimisticRatings, state.config.maxWordingsToStore));
                        })
                        .execute((ctx, state) -> {
                            List<Reaction> reactions = new ArrayList<>();
                            for (Persona participant : state.config.focusGroup.participants) {
                                String wordingsText = state.newWordings.stream()
                                        .map(w -> "<message>" + w + "</message>")
                                        .collect(Collectors.joining("\n"));
                                String input = "Your name is " + participant.name + ". " +
                                        "Your identity: " + participant.identity + ".\n\n" +
                                        "React to the following message versions:\n" + wordingsText +
                                        "\n\nAssess whether each achieves: " + state.config.message.objective +
                                        "\nAs a deliverable: " + state.config.message.deliverable +
                                        "\n\nProvide feedback and a list of " + state.newWordings.size() + " Likert ratings.";

                                Reaction reaction = ctx.subtask("Evaluate messages as " + participant.name)
                                        .withInput(input)
                                        .withOutput(Reaction.class)
                                        .useLLM(participant.llModel)
                                        .run();
                                reactions.add(reaction);
                            }

                            List<RatedWording> ratedWordings = IntStream.range(0, state.newWordings.size())
                                    .mapToObj(i -> {
                                        List<LikertRating> ratings = reactions.stream()
                                                .map(r -> r.ratings().get(i))
                                                .collect(Collectors.toList());
                                        return new RatedWording(
                                                state.newWordings.get(i),
                                                state.config.focusGroup.score(ratings)
                                        );
                                    })
                                    .collect(Collectors.toList());

                            List<String> updatedFeedback = new ArrayList<>(state.feedback);
                            updatedFeedback.addAll(state.config.focusGroup.presentFeedback(reactions));

                            return state
                                    .withNewWordings(Collections.emptyList())
                                    .withBestWordings(state.bestWordings.add(ratedWordings, state.config.maxWordingsToStore))
                                    .withFeedback(updatedFeedback);
                        })
                )
                .goal("needed-proposals-reached", builder -> builder
                        .condition(state ->
                                state.bestWordings.best(state.config.minScore).size() >= state.config.numWordingsRequired ||
                                        state.iteration >= state.config.maxIterations
                        )
                )
                .build();

        MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(
                new OpenAILLMClient(openAIApiKey),
                new AnthropicLLMClient(anthropicApiKey)
        );

        AIAgent<GrouperConfig, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a creative messaging expert specialized in crafting impactful communications.")
                .maxIterations(1000)
                .plannerStrategy(strategy)
                .build();

        String result = agent.run(config);
        System.out.println("Final result:");
        System.out.println(result);
    }

    private static String formatIndexed(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append(i).append(". ").append(items.get(i)).append("\n");
        }
        return sb.toString();
    }
}
