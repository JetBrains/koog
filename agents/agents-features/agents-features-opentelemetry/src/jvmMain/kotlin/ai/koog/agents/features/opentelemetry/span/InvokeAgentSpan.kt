package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.prompt.llm.LLMProvider
import io.opentelemetry.api.trace.SpanKind

/**
 * Agent Run Span
 */
internal class InvokeAgentSpan(
    parent: CreateAgentSpan,
    private val provider: LLMProvider,
    private val runId: String,
    private val agentId: String,
    private val strategyName: String,
) : GenAIAgentSpan(parent) {

    companion object {
        fun createId(agentId: String, runId: String): String =
            createIdFromParent(parentId = CreateAgentSpan.createId(agentId), runId = runId)

        private fun createIdFromParent(parentId: String, runId: String): String =
            "$parentId.run.$runId"
    }

    override val kind: SpanKind = SpanKind.CLIENT

    override val spanId: String = createIdFromParent(parent.spanId, runId)

    /**
     * Add the necessary attributes for the Invoke Agent Span, according to the Open Telemetry Semantic Convention:
     * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#invoke-agent-span
     *
     * Attribute description:
     * - gen_ai.operation.name (required)
     * - gen_ai.system (required)
     * - error.type (conditional)
     * - gen_ai.agent.description (conditional)
     * - gen_ai.agent.id (conditional)
     * - gen_ai.agent.name (conditional)
     * - gen_ai.conversation.id (conditional)
     * - gen_ai.data_source.id (conditional)
     * - gen_ai.output.type (conditional/required)
     * - gen_ai.request.choice.count (conditional/required)
     * - gen_ai.request.model (conditional/required)
     * - gen_ai.request.seed (conditional/required)
     * - server.port (conditional/required)
     * - gen_ai.request.frequency_penalty (recommended)
     * - gen_ai.request.max_tokens (recommended)
     * - gen_ai.request.presence_penalty (recommended)
     * - gen_ai.request.stop_sequences (recommended)
     * - gen_ai.request.temperature (recommended)
     * - gen_ai.request.top_p (recommended)
     * - gen_ai.response.finish_reasons (recommended)
     * - gen_ai.response.id (recommended)
     * - gen_ai.response.model (recommended)
     * - gen_ai.usage.input_tokens (recommended)
     * - gen_ai.usage.output_tokens (recommended)
     * - server.address (recommended)
     */
    override val attributes: List<Attribute> = buildList {
        // gen_ai.operation.name
        add(SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationNameType.INVOKE_AGENT))

        // gen_ai.system
        add(CommonAttributes.System(provider))

        // gen_ai.agent.id
        add(SpanAttributes.Agent.Id(agentId))

        // gen_ai.conversation.id
        add(SpanAttributes.Conversation.Id(runId))

        // custom: strategy name
        add(CustomAttribute("koog.agent.strategy.name",  strategyName))
    }
}
