package ai.koog.agents.features.opentelemetry.metric

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.toSdkAttributes
import ai.koog.agents.features.opentelemetry.extension.getPositiveDurationSec
import ai.koog.agents.features.opentelemetry.metric.MetricsFactory.createOperationDurationHistogram
import ai.koog.agents.features.opentelemetry.metric.MetricsFactory.createTokenCounter
import ai.koog.agents.features.opentelemetry.metric.MetricsFactory.createToolCallCounter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.metrics.Meter

internal class MetricCollector(meter: Meter, private val toolNameStrategy: ToolNameCardinalityStrategy) {
    private val metricEventStorage = MetricEventStorage()

    private val toolCallsCounter = createToolCallCounter(meter)
    private val tokensCounter = createTokenCounter(meter)
    private val operationDurationHistogram = createOperationDurationHistogram(meter)

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    internal fun recordEvent(metricEvent: MetricEvent, isVerbose: Boolean) = when (metricEvent) {
        is LLMCallEnded -> handleLlmCallEnded(metricEvent, isVerbose)
        is ToolCallEnded -> handleToolCallEnded(metricEvent, isVerbose)
        else -> {
            logger.warn { "Unknown metric event type: ${metricEvent::class.simpleName}" }
        }
    }

    private fun handleLlmCallEnded(metricEvent: LLMCallEnded, isVerbose: Boolean) =
        metricEventStorage.endEvent(metricEvent)?.let { (startedEvent, endedEvent) ->
            val inputTokenSpend = endedEvent.inputTokenSpend
            val outputTokenSpend = endedEvent.outputTokenSpend

            inputTokenSpend?.let { inputTokens ->
                tokensCounter.add(
                    inputTokens,
                    listOf(
                        GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION),
                        GenAIAttributes.Provider.Name(metricEvent.modelProvider),
                        GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.INPUT),
                        GenAIAttributes.Response.Model(metricEvent.model)
                    ).toSdkAttributes(isVerbose)
                )
            }
            outputTokenSpend?.let { outputTokens ->
                tokensCounter.add(
                    outputTokens,
                    listOf(
                        GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION),
                        GenAIAttributes.Provider.Name(metricEvent.modelProvider),
                        GenAIAttributes.Token.Type(GenAIAttributes.Token.TokenType.OUTPUT),
                        GenAIAttributes.Response.Model(metricEvent.model)
                    ).toSdkAttributes(isVerbose)
                )
            }
            operationDurationHistogram.record(
                startedEvent.getPositiveDurationSec(endedEvent),
                listOf(
                    GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.TEXT_COMPLETION),
                    GenAIAttributes.Provider.Name(metricEvent.modelProvider),
                    GenAIAttributes.Response.Model(metricEvent.model)
                ).toSdkAttributes(isVerbose)
            )
        }

    private fun handleToolCallEnded(metricEvent: ToolCallEnded, isVerbose: Boolean) =
        metricEventStorage.endEvent(metricEvent)?.let { (startedEvent, endedEvent) ->
            val toolName = toolNameStrategy.apply(metricEvent.toolName)

            val status = when (metricEvent.status) {
                ToolCallStatus.VALIDATION_FAILED -> KoogAttributes.Koog.Tool.Call.StatusType.VALIDATION_FAILED
                ToolCallStatus.SUCCESS -> KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
                ToolCallStatus.FAILED -> KoogAttributes.Koog.Tool.Call.StatusType.ERROR
            }

            operationDurationHistogram.record(
                startedEvent.getPositiveDurationSec(endedEvent),
                listOf(
                    GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL),
                    GenAIAttributes.Tool.Name(toolName),
                    KoogAttributes.Koog.Tool.Call.Status(status)
                ).toSdkAttributes(isVerbose)
            )

            toolCallsCounter.add(
                1,
                listOf(
                    GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL),
                    GenAIAttributes.Tool.Name(toolName),
                    KoogAttributes.Koog.Tool.Call.Status(status)
                ).toSdkAttributes(isVerbose)
            )
        }
}
