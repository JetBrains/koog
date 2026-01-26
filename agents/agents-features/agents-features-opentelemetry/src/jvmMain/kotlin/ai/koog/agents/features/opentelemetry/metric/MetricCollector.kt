package ai.koog.agents.features.opentelemetry.metric

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.extension.getPositiveDurationSec
import ai.koog.agents.features.opentelemetry.extension.put
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter

internal class MetricCollector(meter: Meter) {
    val metricEventStorage = MetricEventStorage()

    val toolCallsCounter = createToolCallCounter(meter)
    val tokensCounter = createTokenCounter(meter)
    val toolCallDurationHistogram = createToolCallDurationHistogram(meter)

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    internal fun recordEvent(metricEvent: MetricEvent) = when (metricEvent) {
        is LLMCallStarted -> handleLlmCallStarted(metricEvent)
        is LLMCallEnded -> handleLlmCallEnded(metricEvent)
        is ToolCallStarted -> handleToolCallStarted(metricEvent)
        is ToolCallEnded -> handleToolCallCompleted(metricEvent)
        else -> {
            logger.warn { "Unknown metric event type: ${metricEvent::class.simpleName}" }
        }
    }

    private fun handleLlmCallStarted(metricEvent: LLMCallStarted) {
        metricEventStorage.startEvent(metricEvent)
    }

    private fun handleLlmCallEnded(metricEvent: LLMCallEnded) =
        metricEventStorage.finishEvent(metricEvent)?.let { (startedEvent, endedEvent) ->
            val inputTokenSpend = endedEvent.inputTokenSpend
            val outputTokenSpend = endedEvent.outputTokenSpend

            inputTokenSpend?.let { inputTokens ->
                tokensCounter.add(
                    inputTokens,
                    Attributes.builder()
                        .put(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION))
                        .put(GenAIAttributes.Provider.Name(metricEvent.modelProvider))
                        .put(GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.INPUT))
                        .put(GenAIAttributes.Response.Model(metricEvent.model))
                        .build()
                )
            }
            outputTokenSpend?.let { outputTokens ->
                tokensCounter.add(
                    outputTokens,
                    Attributes.builder()
                        .put(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION))
                        .put(GenAIAttributes.Provider.Name(metricEvent.modelProvider))
                        .put(GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.OUTPUT))
                        .put(GenAIAttributes.Response.Model(metricEvent.model))
                        .build()
                )
            }
            toolCallDurationHistogram.record(
                startedEvent.getPositiveDurationSec(endedEvent),
                Attributes.builder()
                    .put(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION))
                    .put(GenAIAttributes.Provider.Name(metricEvent.modelProvider))
                    .put(GenAIAttributes.Response.Model(metricEvent.model))
                    .build()
            )
        }

    private fun handleToolCallStarted(metricEvent: ToolCallStarted) {
        metricEventStorage.startEvent(metricEvent)
    }

    private fun handleToolCallCompleted(metricEvent: ToolCallEnded) =
        metricEventStorage.finishEvent(metricEvent)?.let { (startedEvent, endedEvent) ->
            toolCallDurationHistogram.record(
                startedEvent.getPositiveDurationSec(endedEvent),
                Attributes.builder()
                    .put(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL))
                    .put(GenAIAttributes.Tool.Name(metricEvent.toolName))
                    .put(
                        GenAIAttributes.Tool.Call.Status(
                            when (metricEvent.status) {
                                ToolCallStatus.VALIDATION_FAILED -> GenAIAttributes.Tool.Call.StatusType.VALIDATION_FAILED
                                ToolCallStatus.SUCCESS -> GenAIAttributes.Tool.Call.StatusType.SUCCESS
                                ToolCallStatus.FAILED -> GenAIAttributes.Tool.Call.StatusType.ERROR
                            }
                        )
                    )
                    .build()
            )

            toolCallsCounter.add(
                1,
                Attributes.builder()
                    .put(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL))
                    .put(GenAIAttributes.Tool.Name(metricEvent.toolName))
                    .put(GenAIAttributes.Tool.Call.Status(GenAIAttributes.Tool.Call.StatusType.SUCCESS))
                    .build()
            )
        }
}

