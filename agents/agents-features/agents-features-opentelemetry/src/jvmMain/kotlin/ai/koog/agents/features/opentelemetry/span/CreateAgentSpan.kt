package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.prompt.llm.LLModel
import io.opentelemetry.api.trace.SpanKind

/**
 * Root Agent Span
 */
internal class CreateAgentSpan(
    val model: LLModel,
    val agentId: String,
) : GenAIAgentSpan(null) {

    companion object {
        fun createId(agentId: String): String = "agent.$agentId"
    }

    override val spanId = createId(agentId)

    override val kind: SpanKind = SpanKind.CLIENT

    /**
     * Add the necessary attributes for the Create Agent Span according to the Open Telemetry Semantic Convention:
     * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#create-agent-span
     *
     * Attribute description:
     * - gen_ai.operation.name (required)
     * - gen_ai.system (required)
     * - error.type (conditional)
     * - gen_ai.agent.description (conditional)
     * - gen_ai.agent.id (conditional)
     * - gen_ai.agent.name (conditional)
     * - gen_ai.request.model (conditional)
     * - server.port (conditional/required)
     * - server.address (recommended)
     */
    init {
        // gen_ai.operation.name
        addAttribute(SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationNameType.CREATE_AGENT))

        // gen_ai.system
        addAttribute(CommonAttributes.System(model.provider))

        // gen_ai.agent.id
        addAttribute(SpanAttributes.Agent.Id(agentId))

        // gen_ai.request.model
        addAttribute(SpanAttributes.Request.Model(model))
    }
}
