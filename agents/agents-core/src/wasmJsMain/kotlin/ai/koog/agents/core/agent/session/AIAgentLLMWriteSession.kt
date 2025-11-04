@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlin.reflect.KClass

@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
public actual class AIAgentLLMWriteSession internal actual constructor(
    environment: AIAgentEnvironment,
    executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    toolRegistry: ToolRegistry,
    prompt: Prompt,
    model: LLModel,
    config: AIAgentConfig,
    clock: Clock,
    @PublishedApi internal actual val delegate: AIAgentLLMWriteSessionImpl
) : AIAgentLLMSession, AIAgentLLMWriteSessionAPI by delegate {

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int
    ): Flow<SafeTool.Result<TResult>> = with(delegate) { toParallelToolCallsImpl(safeTool, concurrency) }

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        tool: Tool<TArgs, TResult>,
        concurrency: Int
    ): Flow<SafeTool.Result<TResult>> = with(delegate) { toParallelToolCallsImpl(tool, concurrency) }

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int
    ): Flow<SafeTool.Result<TResult>> = with(delegate) { toParallelToolCallsImpl(toolClass, concurrency) }

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRaw(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int
    ): Flow<String> = with(delegate) { toParallelToolCallsRawImpl(safeTool, concurrency) }

    public actual inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRaw(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int
    ): Flow<String> = with(delegate) { toParallelToolCallsRawImpl(toolClass, concurrency) }
}
