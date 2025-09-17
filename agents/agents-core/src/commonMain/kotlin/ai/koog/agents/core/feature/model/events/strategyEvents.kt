package ai.koog.agents.core.feature.model.events

import kotlinx.serialization.Serializable

/**
 * Represents an event triggered at the start of an AI agent strategy execution.
 *
 * This event captures information about the strategy being initiated, allowing
 * for tracking and analyzing the lifecycle of AI agent strategies. It provides
 * details specific to the strategy itself, such as the name, while inheriting
 * shared properties from the [DefinedFeatureEvent] superclass.
 *
 * @property strategyName The name of the strategy being started.
 * @property eventId A string representing the event type.
 */
@Serializable
public data class AIAgentStrategyStartEvent(
    val runId: String,
    val strategyName: String,
    override val eventId: String = AIAgentStrategyStartEvent::class.simpleName!!
) : DefinedFeatureEvent()

/**
 * Event that represents the completion of an AI agent's strategy execution.
 *
 * This event captures information about the strategy that was executed and the result of its execution.
 * It is used to notify the system or consumers about the conclusion of a specific strategy.
 *
 * @property strategyName The name of the strategy that was executed.
 * @property result The result of the strategy execution, providing details such as success, failure,
 * or other status descriptions.
 * @property eventId A string representing the event type.
 */
@Serializable
public data class AIAgentStrategyFinishedEvent(
    val runId: String,
    val strategyName: String,
    val result: String?,
    override val eventId: String = AIAgentStrategyFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()
