@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse

@Suppress("MissingKDocForPublicAPI")
public actual abstract class AIAgentFunctionalContextBase<Pipeline : AIAgentPipeline> internal actual constructor(
    @PublishedApi
    internal actual val delegate: AIAgentFunctionalContextBaseImpl<Pipeline>
) : AIAgentFunctionalContextBaseAPI<Pipeline> by delegate {

    public actual suspend inline fun <reified T> requestLLMStructured(
        message: String,
        examples: List<T>,
        fixingParser: StructureFixingParser?
    ): Result<StructuredResponse<T>> = delegate.requestLLMStructured(message, examples, fixingParser)

    public actual suspend inline fun <Input, reified Output> subtask(
        taskDescription: String,
        input: Input,
        tools: List<Tool<*, *>>?,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
    ): Output = delegate.subtaskImpl(
        taskDescription,
        input,
        tools,
        llmModel,
        llmParams,
        runMode,
        assistantResponseRepeatMax,
    )
}
