package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.extension.addCommonErrorAttributes
import ai.koog.agents.features.opentelemetry.extension.systemMessages
import ai.koog.agents.features.opentelemetry.extension.toStatusData
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.Tracer

/**
 * Build and start a new Create Agent Span with necessary attributes.
 *
 * Add the necessary attributes for the Create Agent Span according to the OpenTelemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#create-agent-span
 *
 * Span attributes:
 * - gen_ai.operation.name (required)
 * - gen_ai.provider.name (required)
 * - gen_ai.agent.description (conditional)
 * - gen_ai.agent.id (conditional)
 * - gen_ai.agent.name (conditional)
 * - gen_ai.request.model (conditional)
 * - gen_ai.system_instructions (recommended)
 * - server.port (conditional/required)
 * - server.address (recommended)
 *
 * Custom attributes:
 * - koog.event.id
 *
 * @param tracer The tracer to use
 * @param contextFactory The context factory to use
 * @param parentSpan The parent span to use
 * @param id The id of the span
 * @param agentId The id of the agent
 * @param model The model to use
 * @param messages The messages to use
 * @param spanAdapter The span adapter to use
 */
internal fun startCreateAgentSpan(
    tracer: Tracer,
    contextFactory: ContextFactory,
    parentSpan: GenAIAgentSpan?,
    id: String,
    agentId: String,
    model: LLModel,
    messages: List<Message>,
    spanAdapter: SpanAdapter? = null,
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.CREATE_AGENT,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.CLIENT,
        name = "${GenAIAttributes.Operation.OperationNameType.CREATE_AGENT.id} $agentId",
    )
        // gen_ai.operation.name
        .addAttribute(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.CREATE_AGENT))
        // gen_ai.provider.name
        .addAttribute(GenAIAttributes.Provider.Name(model.provider))
        // gen_ai.request.model
        .addAttribute(GenAIAttributes.Request.Model(model))
        // gen_ai.agent.description - Ignore. Not supported in Koog
        // gen_ai.agent.id
        .addAttribute(GenAIAttributes.Agent.Id(agentId))

    // gen_ai.agent.name - Ignore. Not supported in Koog
    // server.port - Ignore. Not supported in Koog
    // server.address - Ignore. Not supported in Koog
    // gen_ai.system_instructions
    val systemMessages = messages.systemMessages()
    if (systemMessages.isNotEmpty()) {
        builder.addAttribute(GenAIAttributes.SystemInstructions(systemMessages))
    }

    builder.addAttribute(KoogAttributes.Koog.Event.Id(id))

    val span = builder.buildAndStart(tracer, contextFactory)
    spanAdapter?.onBeforeSpanStarted(span)
    return span
}

/**
 * End Create Agent Span and set final attributes.
 *
 * Add the necessary attributes for the Create Agent Span according to the OpenTelemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#create-agent-span
 *
 * Span attribute:
 * - error.type (conditional)
 *
 * @param span The span to end
 * @param error The error to set as the span status
 * @param verbose Whether to log verbose information
 * @param spanAdapter The span adapter to use
 */
internal fun endCreateAgentSpan(
    span: GenAIAgentSpan,
    error: Throwable? = null,
    verbose: Boolean = false,
    spanAdapter: SpanAdapter? = null,
) {
    check(span.type == SpanType.CREATE_AGENT) {
        "${span.logString} Expected to end span type of type: <${SpanType.CREATE_AGENT}>, but received span of type: <${span.type}>"
    }

    // error.type
    span.addCommonErrorAttributes(error)

    spanAdapter?.onBeforeSpanFinished(span)
    span.end(error.toStatusData(), verbose)
}
