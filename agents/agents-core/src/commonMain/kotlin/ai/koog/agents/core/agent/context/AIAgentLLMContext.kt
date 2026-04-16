@file:OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import kotlin.time.Clock

/**
 * Annotation for marking APIs as detached prompt executors within the `AIAgentLLMContext`.
 *
 * Using APIs annotated with this requires opting in, as calls to `PromptExecutor` will be disconnected
 * from the agent logic. This means these calls will not affect the agent's state or adhere to the
 * `ToolsConversionStrategy`.
 *
 * This API should be used with caution, as it provides functionality that operates outside the
 * standard agent lifecycle and processing logic.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Calls to PromptExecutor used from `AIAgentLLMContext` will not be connected to the agent logic, " +
        "and will not impact the agent's state. " +
        "Other than that, `ToolsConversionStrategy` will not be applied. " +
        "Please be cautious when using this API."
)
public annotation class DetachedPromptExecutorAPI

/**
 * Represents the context for an AI agent LLM, managing tools, prompt handling, and interaction with the
 * environment and execution layers. It provides mechanisms for concurrent read and write operations
 * through sessions, ensuring thread safety.
 *
 * It inherits all shared behavior from [AIAgentLLMContextCommon].
 */
/**
 * Constructs a new instance of `AIAgentLLMContext` with the provided parameters.
 *
 * @param tools A list of tools described by [ToolDescriptor] that the agent can interact with.
 * @param toolRegistry A registry of available tools, defaulting to an empty [ToolRegistry].
 * @param prompt The initial prompt used in the context, represented by a [Prompt] instance.
 * @param model The language model used for processing prompts and generating responses.
 * @param responseProcessor An optional [ResponseProcessor] for handling and processing model responses.
 * @param promptExecutor Responsible for executing the logic for prompt processing in the context.
 * @param environment The operational environment of the AI agent, represented by an [AIAgentEnvironment].
 * @param config Configuration settings for the AI agent, encapsulated in an [AIAgentConfig].
 * @param clock A clock instance for managing time-related operations within the context.
 */
public expect open class AIAgentLLMContext(
    tools: List<ToolDescriptor>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    prompt: Prompt,
    model: LLModel,
    responseProcessor: ResponseProcessor?,
    promptExecutor: PromptExecutor,
    environment: AIAgentEnvironment,
    config: AIAgentConfig,
    clock: Clock
) : AIAgentLLMContextCommon
