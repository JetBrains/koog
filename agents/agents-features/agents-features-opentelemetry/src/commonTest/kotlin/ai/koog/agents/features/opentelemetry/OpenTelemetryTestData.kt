package ai.koog.agents.features.opentelemetry

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import io.opentelemetry.kotlin.tracing.data.SpanData

internal data class OpenTelemetryTestData(
    var result: String? = null,
    var collectedSpans: List<SpanData> = emptyList(),
) {

    val runIds: List<String>
        get() = collectedSpans.mapNotNull { span ->
            span.attributes["gen_ai.conversation.id"] as? String
        }.distinct()

    val lastRunId: String
        get() = runIds.last()

    fun singleCreateAgentEventIds(agentId: String): String {
        return filterCreateAgentEventIds(agentId).singleOrNull()
            ?: throw NoSuchElementException("Expected collected create agent with agent id '$agentId' to be present.")
    }

    fun singleStrategyEventIds(strategyName: String): String {
        return filterStrategyEventIds(strategyName).singleOrNull()
            ?: throw NoSuchElementException("Expected collected strategy with strategy name '$strategyName' to be present.")
    }

    fun singleNodeEventIdByNodeId(nodeId: String): String {
        return filterNodeEventIdsByNodeId(nodeId).singleOrNull()
            ?: throw NoSuchElementException("Expected collected node with node id '$nodeId' to be present.")
    }

    fun singleSubgraphEventIdBySubgraphId(subgraphId: String): String {
        return filterSubgraphEventIdBySubgraphId(subgraphId).singleOrNull()
            ?: throw NoSuchElementException("Expected collected subgraph with subgraph id '$subgraphId' to be present.")
    }

    fun singleToolCallEventIdByToolName(toolName: String): String {
        return filterToolCallEventIdByToolName(toolName).singleOrNull()
            ?: throw NoSuchElementException("Expected collected tool call ids for tool with name '$toolName' gto be present.")
    }

    fun filterCreateAgentEventIds(agentId: String): List<String> {
        val operationNameAttribute = GenAIAttributes.Operation.Name(OperationNameType.CREATE_AGENT)
        val agentIdAttribute = GenAIAttributes.Agent.Id(agentId)
        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")

        return collectedSpans.filter { span ->
            val attributeValue = span.attributes[operationNameAttribute.key]
            val agentIdValue = span.attributes[agentIdAttribute.key]

            attributeValue != null &&
                attributeValue == operationNameAttribute.value &&
                agentIdValue != null &&
                agentIdValue == agentIdAttribute.value
        }
            .mapNotNull { span -> span.attributes[eventIdAttribute.key] as? String }
    }

    fun filterStrategyEventIds(strategyName: String): List<String> {
        val strategyAttribute = KoogAttributes.Koog.Strategy.Name(strategyName)
        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")

        return collectedSpans.filter { span ->
            val attributeValue = span.attributes[strategyAttribute.key]
            attributeValue != null && attributeValue == strategyAttribute.value
        }
            .mapNotNull { span -> span.attributes[eventIdAttribute.key] as? String }
    }

    fun filterNodeEventIdsByNodeId(nodeId: String): List<String> {
        val nodeAttribute = KoogAttributes.Koog.Node.Id(nodeId)
        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")

        return collectedSpans.filter { span ->
            val nodeIdAttribute = span.attributes[nodeAttribute.key]
            nodeIdAttribute != null && nodeIdAttribute == nodeAttribute.value
        }.mapNotNull { span -> span.attributes[eventIdAttribute.key] as? String }
    }

    fun filterSubgraphEventIdBySubgraphId(subgraphId: String): List<String> {
        val subgraphAttribute = KoogAttributes.Koog.Subgraph.Id(subgraphId)
        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")

        return collectedSpans.filter { span ->
            val subgraphIdAttribute = span.attributes[subgraphAttribute.key]
            subgraphIdAttribute != null && subgraphIdAttribute == subgraphAttribute.value
        }.mapNotNull { span -> span.attributes[eventIdAttribute.key] as? String }
    }

    fun filterToolCallEventIdByToolName(toolName: String): List<String> {
        val toolNameAttribute = GenAIAttributes.Tool.Name(toolName)
        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")

        return collectedSpans.filter { span ->
            val attributeValue = span.attributes[toolNameAttribute.key]
            attributeValue != null && attributeValue == toolNameAttribute.value
        }
            .mapNotNull { span -> span.attributes[eventIdAttribute.key] as? String }
    }

    fun filterInferenceEventIds(): List<String> {
        val operationNameAttribute = GenAIAttributes.Operation.Name(OperationNameType.CHAT)
        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")

        return collectedSpans.filter { span ->
            val attributeValue = span.attributes[operationNameAttribute.key]
            attributeValue != null && attributeValue == operationNameAttribute.value
        }
            .mapNotNull { span -> span.attributes[eventIdAttribute.key] as? String }
    }

    fun singleAttributeValue(spanData: SpanData, key: String): String? {
        return spanData.attributes[key]?.toString()
    }

    fun filterCreateAgentSpans(): List<SpanData> {
        val createAgentAttribute = GenAIAttributes.Operation.Name(OperationNameType.CREATE_AGENT)

        return collectedSpans.filter { spanData ->
            spanData.attributes[createAgentAttribute.key] == createAgentAttribute.value
        }
    }

    fun filterAgentInvokeSpans(): List<SpanData> {
        val invokeAgentAttribute = GenAIAttributes.Operation.Name(OperationNameType.INVOKE_AGENT)

        return collectedSpans.filter { spanData ->
            spanData.attributes[invokeAgentAttribute.key] == invokeAgentAttribute.value
        }
    }

    fun filterStrategySpans(): List<SpanData> {
        val strategyAttribute = KoogAttributes.Koog.Strategy.Name("")

        return collectedSpans.filter { spanData ->
            spanData.attributes[strategyAttribute.key] != null
        }
    }

    fun filterInferenceSpans(): List<SpanData> {
        val chatAttribute = GenAIAttributes.Operation.Name(OperationNameType.CHAT)

        return collectedSpans.filter { spanData ->
            spanData.attributes[chatAttribute.key] == chatAttribute.value
        }
    }

    fun filterExecuteToolSpans(): List<SpanData> {
        val executeToolOperationAttribute = GenAIAttributes.Operation.Name(OperationNameType.EXECUTE_TOOL)

        return collectedSpans.filter { spanData ->
            spanData.attributes[executeToolOperationAttribute.key] == executeToolOperationAttribute.value
        }
    }

    fun filterNodeExecutionSpans(): List<SpanData> {
        val nodeAttribute = KoogAttributes.Koog.Node.Id("")

        return collectedSpans.filter { spanData -> spanData.attributes[nodeAttribute.key] != null }
    }

    fun filterSubgraphExecutionSpans(): List<SpanData> {
        val subgraphAttribute = KoogAttributes.Koog.Subgraph.Id("")

        return collectedSpans.filter { spanData -> spanData.attributes[subgraphAttribute.key] != null }
    }
}
