@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import kotlin.time.Clock

/**
 * Pipeline for AI agent features that provides interception points for various agent lifecycle events.
 *
 * The pipeline allows features to:
 * - Be installed into agent contexts
 * - Intercept agent creation and environment transformation
 * - Intercept strategy execution before and after it happens
 * - Intercept node execution before and after it happens
 * - Intercept LLM calls before and after they happen
 * - Intercept tool calls before and after they happen
 *
 * This pipeline serves as the central mechanism for extending and customizing agent behavior
 * through a flexible interception system. Features can be installed with custom configurations
 * and can hook into different stages of the agent's execution lifecycle.
 *
 * All methods are inherited from [AIAgentPipelineCommon]. Platform-specific implementations
 * may add additional JVM/JS-specific interceptor overloads for Java interoperability.
 */
public expect abstract class AIAgentPipeline(
    config: AIAgentConfig,
    clock: Clock
) : AIAgentPipelineCommon
