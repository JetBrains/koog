package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.session.requestLLMStructured
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import kotlinx.serialization.Serializable

/**
 * Represents the result of a plan evaluation performed by an LLM (Large Language Model).
 *
 * This class is primarily used within internal agent-related implementations where an LLM
 * evaluates the correctness of a plan and optionally provides feedback for improvements.
 *
 * @property isCorrect Indicates whether the evaluated plan is correct.
 * @property feedback Optional feedback provided by the LLM about the evaluated plan. This property
 *        is populated only when the plan is deemed incorrect (`isCorrect == false`) and adjustments
 *        are suggested.
 */
@InternalAgentsApi
@Serializable
@LLMDescription("Result of the evaluation")
public data class CriticResultFromLLM(
    @property:LLMDescription("Was the task solved correctly?")
    val isCorrect: Boolean,
    @property:LLMDescription(
        "Optional feedback about the provided solution. " +
            "Only needed if `isCorrect == false` and if solution needs adjustments."
    )
    val feedback: String
)

/**
 * Represents the result of a critique or feedback process.
 *
 * @property successful Indicates whether the critique operation was successful.
 * @property feedback A textual message providing details about the*/
public data class CriticResult<T>(
    val successful: Boolean,
    val feedback: String,
    val input: T
)

/**
 * A method to utilize a language model (LLM) as a critic or judge for evaluating tasks with context-aware feedback.
 * This method processes a given task and the interaction history to provide structured feedback on the task's correctness.
 *
 * @param llmModel The optional language model to override the default model during the session. If `null`, the default model will be used.
 * @param task The task or instruction to be presented to the language model for critical evaluation.
 */
@OptIn(InternalAgentsApi::class)
public inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.llmAsAJudge(
    llmModel: LLModel? = null,
    task: String
): AIAgentNodeDelegate<T, CriticResult<T>> = node<T, CriticResult<T>> { nodeInput ->
    llm.writeSession {
        val initialPrompt = prompt.copy()
        val initialModel = model

        prompt = prompt("critic") {
            // Combine all history into one message with XML tags
            // to prevent LLM from continuing answering in a tool_call -> tool_result pattern
            val combinedMessage = buildString {
                append("<previous_conversation>\n")
                initialPrompt.messages.forEach { message ->
                    when (message) {
                        is Message.System -> append("<system>\n${message.content}\n</system>\n")
                        is Message.User -> append("<user>\n${message.content}\n</user>\n")
                        is Message.Assistant -> append("<assistant>\n${message.content}\n</assistant>\n")
                        is Message.Reasoning -> append("<thinking>\n${message.content}\n</thinking>\n")
                        is Message.Tool.Call -> append(
                            "<tool_call tool=${message.tool}>\n${message.content}\n</tool_call>\n"
                        )

                        is Message.Tool.Result -> append(
                            "<tool_result tool=${message.tool}>\n${message.content}\n</tool_result>\n"
                        )
                    }
                }
                append("</previous_conversation>\n")
            }

            // Put Critic Task as a System instruction
            system(task)
            // And rest of the history -- in a combined XML message
            user(combinedMessage)
        }

        if (llmModel != null) {
            model = llmModel
        }

        val result = requestLLMStructured<CriticResultFromLLM>(
            // optional field -- recommented for LLM awareness and reliability of the output
            examples = listOf(
                CriticResultFromLLM(
                    isCorrect = true,
                    feedback = "All good"
                ),
                CriticResultFromLLM(
                    isCorrect = false,
                    feedback = "Following parts of the plan have problems: *, *, *. Please consider changing ..."
                )
            ),
            // optional field -- recommented for reliability of the format
            fixingParser = StructureFixingParser(
                model = OpenAIModels.Chat.GPT4oMini,
                retries = 3,
            )
        ).getOrThrow().data

        prompt = initialPrompt
        model = initialModel

        CriticResult(
            successful = result.isCorrect,
            feedback = result.feedback,
            input = nodeInput
        )
    }
}
