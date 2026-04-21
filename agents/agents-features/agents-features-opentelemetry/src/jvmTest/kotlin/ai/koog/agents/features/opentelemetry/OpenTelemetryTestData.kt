package ai.koog.agents.features.opentelemetry

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData

internal data class OpenTelemetryTestData(
    var result: String? = null,
    var collectedSpans: List<SpanData> = emptyList(),
) {

    val runIds: List<String>
        get() = collectedSpans.mapNotNull { span ->
            span.attributes[AttributeKey.stringKey("gen_ai.conversation.id")]
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
        val expectedOperationNameKey = AttributeKey.stringKey(operationNameAttribute.key)

        val agentIdAttribute = GenAIAttributes.Agent.Id(agentId)
        val expectedAgentIdAttributeKey = AttributeKey.stringKey(agentIdAttribute.key)

        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")
        val expectedEventIdKey = AttributeKey.stringKey(eventIdAttribute.key)

        return collectedSpans.filter { span ->
            val attributeValue = span.attributes.get(expectedOperationNameKey)
            val agentIdValue = span.attributes.get(expectedAgentIdAttributeKey)

            attributeValue != null &&
                attributeValue == operationNameAttribute.value &&
                agentIdValue != null &&
                agentIdValue == agentIdAttribute.value
        }
            .mapNotNull { span -> span.attributes?.get(expectedEventIdKey) }
    }

    fun filterStrategyEventIds(strategyName: String): List<String> {
        val strategyAttribute = KoogAttributes.Koog.Strategy.Name(strategyName)
        val expectedStrategyNameKey = AttributeKey.stringKey(strategyAttribute.key)

        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")
        val expectedEventIdKey = AttributeKey.stringKey(eventIdAttribute.key)

        return collectedSpans.filter { span ->
            val attributeValue = span.attributes.get(expectedStrategyNameKey)
            attributeValue != null && attributeValue == strategyAttribute.value
        }
            .mapNotNull { span -> span.attributes?.get(expectedEventIdKey) }
    }

    fun filterNodeEventIdsByNodeId(nodeId: String): List<String> {
        val nodeAttribute = KoogAttributes.Koog.Node.Id(nodeId)
        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")

        val expectedNodeKey = AttributeKey.stringKey(nodeAttribute.key)
        val expectedEventIdKey = AttributeKey.stringKey(eventIdAttribute.key)

        return collectedSpans.filter { span ->
            val nodeIdAttribute = span.attributes.get(expectedNodeKey)
            nodeIdAttribute != null && nodeIdAttribute == nodeAttribute.value
        }.mapNotNull { span -> span.attributes?.get(expectedEventIdKey) }
    }

    fun filterSubgraphEventIdBySubgraphId(subgraphId: String): List<String> {
        val subgraphAttribute = KoogAttributes.Koog.Subgraph.Id(subgraphId)
        val expectedSubgraphKey = AttributeKey.stringKey(subgraphAttribute.key)

        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")
        val expectedEventIdKey = AttributeKey.stringKey(eventIdAttribute.key)

        return collectedSpans.filter { span ->
            val subgraphIdAttribute = span.attributes.get(expectedSubgraphKey)
            subgraphIdAttribute != null && subgraphIdAttribute == subgraphAttribute.value
        }.mapNotNull { span -> span.attributes?.get(expectedEventIdKey) }
    }

    fun filterToolCallEventIdByToolName(toolName: String): List<String> {
        val toolNameAttribute = GenAIAttributes.Tool.Name(toolName)
        val expectedToolNameKey = AttributeKey.stringKey(toolNameAttribute.key)

        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")
        val expectedEventIdKey = AttributeKey.stringKey(eventIdAttribute.key)

        return collectedSpans.filter { span ->
            val attributeValue = span.attributes.get(expectedToolNameKey)
            attributeValue != null && attributeValue == toolNameAttribute.value
        }
            .mapNotNull { span -> span.attributes?.get(expectedEventIdKey) }
    }

    fun filterInferenceEventIds(): List<String> {
        val operationNameAttribute = GenAIAttributes.Operation.Name(OperationNameType.CHAT)
        val expectedOperationNameKey = AttributeKey.stringKey(operationNameAttribute.key)

        val eventIdAttribute = KoogAttributes.Koog.Event.Id("")
        val expectedEventIdKey = AttributeKey.stringKey(eventIdAttribute.key)

        return collectedSpans.filter { span ->
            val attributeValue = span.attributes.get(expectedOperationNameKey)
            attributeValue != null && attributeValue == operationNameAttribute.value
        }
            .mapNotNull { span -> span.attributes?.get(expectedEventIdKey) }
    }

    fun singleAttributeValue(spanData: SpanData, key: String): String? {
        return spanData.attributes?.asMap()?.mapKeys { it.key.key }[key]?.toString()
    }

    fun filterCreateAgentSpans(): List<SpanData> {
        val createAgentAttribute = GenAIAttributes.Operation.Name(OperationNameType.CREATE_AGENT)
        val attributeKey = AttributeKey.stringKey(createAgentAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == createAgentAttribute.value
        }
    }

    fun filterAgentInvokeSpans(): List<SpanData> {
        val invokeAgentAttribute = GenAIAttributes.Operation.Name(OperationNameType.INVOKE_AGENT)
        val attributeKey = AttributeKey.stringKey(invokeAgentAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == invokeAgentAttribute.value
        }
    }

    fun filterStrategySpans(): List<SpanData> {
        val strategyAttribute = KoogAttributes.Koog.Strategy.Name("")
        val attributeKey = AttributeKey.stringKey(strategyAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) != null
        }
    }

    fun filterInferenceSpans(): List<SpanData> {
        val chatAttribute = GenAIAttributes.Operation.Name(OperationNameType.CHAT)
        val attributeKey = AttributeKey.stringKey(chatAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == chatAttribute.value
        }
    }

    fun filterExecuteToolSpans(): List<SpanData> {
        val executeToolOperationAttribute = GenAIAttributes.Operation.Name(OperationNameType.EXECUTE_TOOL)
        val attributeKey = AttributeKey.stringKey(executeToolOperationAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == executeToolOperationAttribute.value
        }
    }

    fun filterNodeExecutionSpans(): List<SpanData> {
        val nodeAttribute = KoogAttributes.Koog.Node.Id("")
        val expectedNodeKey = AttributeKey.stringKey(nodeAttribute.key)

        return collectedSpans.filter { spanData -> spanData.attributes.get(expectedNodeKey) != null }
    }

    fun filterSubgraphExecutionSpans(): List<SpanData> {
        val subgraphAttribute = KoogAttributes.Koog.Subgraph.Id("")
        val expectedSubgraphKey = AttributeKey.stringKey(subgraphAttribute.key)

        return collectedSpans.filter { spanData -> spanData.attributes.get(expectedSubgraphKey) != null }
    }
}
