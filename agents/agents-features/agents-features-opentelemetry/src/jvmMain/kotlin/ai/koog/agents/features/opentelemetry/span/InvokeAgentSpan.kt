package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.prompt.llm.LLMProvider
import io.opentelemetry.api.trace.SpanKind

/**
 * Agent Run Span
 */
internal class InvokeAgentSpan(
    parent: CreateAgentSpan,
    provider: LLMProvider,
    runId: String,
    agentId: String,
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
    init {
        // gen_ai.operation.name
        addAttribute(SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationNameType.INVOKE_AGENT))

        // gen_ai.system
        addAttribute(CommonAttributes.System(provider))

        // gen_ai.agent.id
        addAttribute(SpanAttributes.Agent.Id(agentId))

        // gen_ai.conversation.id
        addAttribute(SpanAttributes.Conversation.Id(runId))
    }
}
