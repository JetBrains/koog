package ai.koog.agents.features.eventHandler.feature

/**
 * Configuration class for the EventHandler feature.
 *
 * This class provides a way to configure handlers for various events that occur during
 * the execution of an agent. These events include agent lifecycle events, strategy events,
 * node events, LLM call events, and tool call events.
 *
 * Each handler is a property that can be assigned a lambda function to be executed when
 * the corresponding event occurs.
 *
 * Example usage:
 * ```
 * handleEvents {
 *     onToolCallStarting { eventContext ->
 *         println("Tool called: ${eventContext.tool.name} with args ${eventContext.toolArgs}")
 *     }
 *
 *     onAgentCompleted { eventContext ->
 *         println("Agent finished with result: ${eventContext.result}")
 *     }
 * }
 * ```
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect open class EventHandlerConfig constructor() : EventHandlerConfigCommon
