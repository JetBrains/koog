package ai.koog.agents.features.opentelemetry

import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData

internal data class NodeInfo(val nodeId: String, val eventId: String)

internal data class OpenTelemetryTestData(
    var result: String? = null,
    var collectedSpans: List<SpanData> = emptyList(),
    var collectedNodeIds: List<NodeInfo> = emptyList(),
    var collectedSubgraphIds: List<NodeInfo> = emptyList(),
    var collectedLLMEventIds: List<String> = emptyList(),
    var collectedToolEventIds: List<String> = emptyList(),
) {

    companion object {
        private val nodeIdAttributeKey = AttributeKey.stringKey("koog.node.id")
        private val subgraphIdAttributeKey = AttributeKey.stringKey("koog.subgraph.id")
    }

    val runIds: List<String>
        get() = collectedSpans.mapNotNull { span ->
            span.attributes[AttributeKey.stringKey("gen_ai.conversation.id")]
        }.distinct()

    val lastRunId: String
        get() = runIds.last()

    fun singleNodeInfoById(nodeId: String): NodeInfo = collectedNodeIds.singleOrNull { it.nodeId == nodeId }
        ?: throw NoSuchElementException("Expected collected node with node id '$nodeId' to be present.")

    fun singleSubgraphInfoById(nodeId: String): NodeInfo = collectedSubgraphIds.singleOrNull { it.nodeId == nodeId }
        ?: throw NoSuchElementException("Expected collected subgraphs with subgraph id '$nodeId' to be present.")

    fun filterNodeInfoById(nodeId: String): List<NodeInfo> = collectedNodeIds.filter { it.nodeId == nodeId }

    fun filterSubgraphInfoById(nodeId: String): List<NodeInfo> = collectedSubgraphIds.filter { it.nodeId == nodeId }

    fun filterCreateAgentSpans(): List<SpanData> {
        val createAgentAttribute = SpanAttributes.Operation.Name(OperationNameType.CREATE_AGENT)
        val attributeKey = AttributeKey.stringKey(createAgentAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == createAgentAttribute.value
        }
    }

    fun filterAgentInvokeSpans(): List<SpanData> {
        val invokeAgentAttribute = SpanAttributes.Operation.Name(OperationNameType.INVOKE_AGENT)
        val attributeKey = AttributeKey.stringKey(invokeAgentAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == invokeAgentAttribute.value
        }
    }

    fun filterInferenceSpans(): List<SpanData> {
        val chatAttribute = SpanAttributes.Operation.Name(OperationNameType.CHAT)
        val attributeKey = AttributeKey.stringKey(chatAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == chatAttribute.value
        }
    }

    fun filterExecuteToolSpans(): List<SpanData> {
        val executeToolOperationAttribute = SpanAttributes.Operation.Name(OperationNameType.EXECUTE_TOOL)
        val attributeKey = AttributeKey.stringKey(executeToolOperationAttribute.key)

        return collectedSpans.filter { spanData ->
            spanData.attributes.get(attributeKey) == executeToolOperationAttribute.value
        }
    }

    fun filterNodeExecutionSpans(): List<SpanData> {
        return collectedSpans.filter { spanData -> spanData.attributes.get(nodeIdAttributeKey) != null }
    }

    fun filterSubgraphExecutionSpans(): List<SpanData> {
        return collectedSpans.filter { spanData -> spanData.attributes.get(subgraphIdAttributeKey) != null }
    }
}
