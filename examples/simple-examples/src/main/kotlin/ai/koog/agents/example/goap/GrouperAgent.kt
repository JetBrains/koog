package ai.koog.agents.example.goap

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.agents.planner.goap.goap
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlin.reflect.typeOf

suspend fun AIAgentFunctionalContext.generateProposal(
    config: GrouperConfig,
    bestWordings: BestWordings,
    feedback: List<String>,
    learnings: List<String>
) = llm.writeSession {
    val creative = config.creatives.nextCreative()
    val newPrompt = prompt(prompt.id, creative.llmParams) {
        system(
            """
            You are a creative messaging expert specialized in crafting impactful communications.
            Your task is to generate message variations that achieve specific objectives.

            Guidelines:
            - Each message should be clear, concise and impactful
            - Focus on the intended outcome and target audience
            - Consider psychological and emotional aspects
            - Maintain appropriate tone for the medium
            - Stay within character limits for the deliverable type
            - Format each proposal as a separate line
            - Never exceed the requested number of proposals
            """.trimIndent()
        )

        user(
            """
                OBJECTIVE: ${config.message.objective}
                DELIVERABLE: ${config.message.deliverable}

                Previous feedback:
                ${feedback.withIndex().joinToString("\n") { (i, s) -> "$i. $s" }}

                Previous learnings:
                ${learnings.withIndex().joinToString("\n") { (i, s) -> "$i. $s" }}

                Task:
                1. Analyze previous high-scoring messages
                2. Create up to ${config.numProposals} new variations
                3. Each proposal should aim to achieve the objective while fitting the deliverable format

                Current top performing messages:
                ${bestWordings.show(config.numWordingsToShow)}

                Provide your proposals as a numbered list, one per line.
            """.trimIndent()
        )
    }

    prompt = newPrompt
    model = creative.llModel
    requestLLMStructured<Proposal>().getOrThrow().data
}

suspend fun AIAgentFunctionalContext.evaluateWordings(
    config: GrouperConfig,
    wordings: List<String>,
): List<Reaction> {
    val task = """
        React to the following wording versions:

        ${wordings.joinToString("\n") { "<message>$it</message>" }}

        Assess in terms of whether it would produce the following objective in your mind:
        <objective>${config.message.objective}</objective>
        Also consider whether it is effective as <deliverable>${config.message.deliverable}</deliverable>
        
        You should provide an overall feedback highlighting the positive and negative aspects of the wordings.
        Also provide a list of ${wordings.size} likert ratings to give the assessment to every specific wording.
    """.trimIndent()

    return supervisorScope {
        config.focusGroup.participants.map { participant ->
            async {
                llm.writeSession {
                    val newPrompt = prompt(prompt.id, participant.llmParams) {
                        system(
                            """
                                Your name is ${participant.name}. Your identity is ${participant.identity}.

                                You are a member of a focus group.
                                Your replies are confidential and you don't need to worry about
                                anyone knowing what you said, so you can share your feelings
                                honestly without fear of judgment or consequences.

                                IMPORTANT: Be critical in your evaluation. Do not hesitate to point out flaws, 
                                weaknesses, or potential improvements. Your role is to be objective, not to be 
                                supportive or positive. Focus on what doesn't as well as what does.
                            """.trimIndent()
                        )

                        user(task)
                    }

                    model = participant.llModel
                    prompt = newPrompt

                    requestLLMStructured<Reaction>().getOrThrow().data
                }
            }
        }.awaitAll()
    }
}

fun grouperPlanner() = goap<State>(typeOf<State>()) {
    goal(
        name = "Needed number of good proposals reached"
    ) { state ->
        state.bestWordings.best(state.config.minScore).size >= state.config.numWordingsRequired ||
            state.iteration >= state.config.maxIterations
    }

    action(
        name = "Evolve message wording",
        description = "Previously generated wordings should be already rated",
        precondition = { state -> state.newWordings.isEmpty() },
        belief = { state ->
            val proposal = Proposal.default(state.config.numProposals)
            state.copy(
                newWordings = proposal.wordings,
                learnings = state.learnings + proposal.learnings,
                iteration = state.iteration + 1
            )
        }
    ) { ctx, state ->
        // debug
        buildString {
            appendLine("Current best wordings:")
            appendLine(state.bestWordings.show(10))
            appendLine()
        }.also(::println)

        val proposal = ctx.generateProposal(
            state.config,
            state.bestWordings,
            state.feedback,
            state.learnings
        )

        state.copy(
            newWordings = proposal.wordings,
            learnings = state.learnings + proposal.learnings,
            iteration = state.iteration + 1
        )
    }

    action(
        name = "Run focus group",
        description = "The new wordings should not be rated yet",
        precondition = { state -> state.newWordings.isNotEmpty() },
        belief = { state ->
            state.copy(
                newWordings = listOf(),
                bestWordings = state.bestWordings.add(
                    state.newWordings.map { RatedWording(it, 1.0) },
                    state.config.maxWordingsToStore
                ),
            )
        }
    ) { ctx, state ->
        val reactions = ctx.evaluateWordings(
            state.config,
            state.newWordings
        )

        val ratedWordings = state.newWordings.withIndex().map { (i, wording) ->
            RatedWording(
                wording,
                state.config.focusGroup.score(reactions.map { reaction -> reaction.ratings[i] })
            )
        }

        state.copy(
            newWordings = listOf(),
            bestWordings = state.bestWordings.add(ratedWordings, state.config.maxWordingsToStore),
            feedback = state.feedback + state.config.focusGroup.presentFeedback(reactions),
        )
    }
}

suspend fun main() {
    val grouperStrategy = AIAgentPlannerStrategy(
        name = "grouper",
        planner = grouperPlanner(),
    )

    val agentConfig = AIAgentConfig(
        prompt = prompt("grouper") {},
        model = OpenAIModels.Chat.GPT4o, // Default model. Will be overridden by persona models
        maxAgentIterations = 1000,
    )

    val openAIClient = OpenAILLMClient(ApiKeyService.openAIApiKey)
    val anthropicClient = AnthropicLLMClient(ApiKeyService.anthropicApiKey)

    val multiExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenAI to openAIClient,
        LLMProvider.Anthropic to anthropicClient
    )

    multiExecutor.use { multiExecutor ->
        val agent = PlannerAIAgent(
            promptExecutor = multiExecutor,
            strategy = grouperStrategy,
            agentConfig = agentConfig,
        )

        // Create diverse participants with different models and parameters
        val participant1 = Persona(
            "participant1",
            "Alex",
            "A 25-year-old urban professional who values directness and clarity",
            OpenAIModels.Chat.GPT4o,
            LLMParams(temperature = 0.7)
        )

        val participant2 = Persona(
            "participant2",
            "Jordan",
            "A 40-year-old parent concerned about health issues affecting youth",
            OpenAIModels.Chat.GPT4_1,
            LLMParams(temperature = 0.3)
        )

        val participant3 = Persona(
            "participant3",
            "Taylor",
            "A 19-year-old college student who responds to emotional appeals",
            AnthropicModels.Sonnet_4_5,
            LLMParams(temperature = 1.0)
        )

        // Create diverse creatives with different models and parameters
        val creative1 = Persona(
            "creative1",
            "Morgan",
            "An advertising professional specializing in impactful public health campaigns",
            OpenAIModels.Chat.GPT4o,
            LLMParams(temperature = 1.2)
        )

        val creative2 = Persona(
            "creative2",
            "Casey",
            "A copywriter with experience in creating concise, memorable slogans",
            OpenAIModels.Chat.GPT4_1,
            LLMParams(temperature = 0.5)
        )

        val creative3 = Persona(
            "creative3",
            "Riley",
            "A behavioral psychologist who understands persuasive messaging techniques",
            AnthropicModels.Sonnet_4_5,
            LLMParams(temperature = 0.8)
        )

        val participants = listOf(participant1, participant2, participant3)
        val creatives = listOf(creative1, creative2, creative3)
        val message = Message("smoking", "smoking is bad", "deter smoking", "billboard slogan")

        val config = GrouperConfig(
            focusGroup = FocusGroup(participants),
            creatives = Creatives(creatives),
            message = message,
        )

        val result = agent.run(State(config)).result
        buildString {
            appendLine("Final result:")
            appendLine(result)
            appendLine()
        }
    }
}
