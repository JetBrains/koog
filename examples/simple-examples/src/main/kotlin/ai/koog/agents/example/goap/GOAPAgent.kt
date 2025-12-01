package ai.koog.agents.example.goap

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.planner.asStrategy
import ai.koog.agents.planner.goap.GOAPPlanner
import ai.koog.agents.planner.goap.action
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlin.math.max

class GrouperConfig(
    val focusGroup: FocusGroup,
    val creatives: Creatives,
    val message: Message,
    val minScore: Double = 0.7,
    val numWordingsRequired: Int = 10,
    val numWordingsToShow: Int = 20,
    val numProposals: Int = 10,
    val maxIterations: Int = 20,
) {
    val maxWordingsToStore = max(numWordingsRequired, numWordingsToShow)
}

data class State(
    val config: GrouperConfig,
    val bestWordings: BestWordings = BestWordings(),
    val iteration: Int = 0,
    val newWordings: List<String> = emptyList(),
    val feedback: List<String> = emptyList(),
    val learnings: List<String> = emptyList()
) {
    val result get() = bestWordings.show(config.numWordingsRequired)
}

suspend fun AIAgentGraphContextBase.generateProposal(
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

    rewritePrompt {
        newPrompt
    }
    model = creative.llModel
    requestLLMStructured<Proposal>().getOrThrow().data
}

suspend fun AIAgentGraphContextBase.evaluateWordings(
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
                with(fork()) {
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

                        rewritePrompt {
                            newPrompt
                        }

                        requestLLMStructured<Reaction>().getOrThrow().data
                    }
                }
            }
        }.awaitAll()
    }
}

fun grouperStrategy() =
    GOAPPlanner.create<State>("grouper") {
        goal("Needed number of good proposals reached") {
            bestWordings.best(config.minScore).size >= config.numWordingsRequired || iteration >= config.maxIterations
        }

        action(
            "Evolve message wording",
            "Previously generated wordings should be already rated",
            precondition = { newWordings.isEmpty() },
            belief = {
                val proposal = Proposal.default(config.numProposals)
                copy(
                    newWordings = proposal.wordings,
                    learnings = learnings + proposal.learnings,
                    iteration = iteration + 1
                )
            }
        ) { state ->
            // debug
            println(state.bestWordings.show(10))

            val proposal = generateProposal(
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
            "Run focus group",
            "The new wordings should not be rated yet",
            precondition = { newWordings.isNotEmpty() },
            belief = {
                copy(
                    newWordings = listOf(),
                    bestWordings = bestWordings.add(
                        newWordings.map { RatedWording(it, 1.0) },
                        config.maxWordingsToStore
                    ),
                )
            }
        ) { state ->
            val reactions = evaluateWordings(
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
    }.asStrategy()

fun main() {
    // Create LLM clients for both OpenAI and Anthropic
    val openAIClient = OpenAILLMClient(ApiKeyService.openAIApiKey)
    val anthropicClient = AnthropicLLMClient(ApiKeyService.anthropicApiKey)

    // Create a multimodel executor that can handle both OpenAI and Anthropic models
    val multiExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenAI to openAIClient,
        LLMProvider.Anthropic to anthropicClient
    )

    val agent = AIAgent(
        promptExecutor = multiExecutor,
        llmModel = OpenAIModels.Chat.GPT4o, // Default model. Will be overridden by persona models
        strategy = grouperStrategy(),
        maxIterations = 1000,
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

    val result = runBlocking {
        agent.run(State(config))
    }.result

    println(result)
}
