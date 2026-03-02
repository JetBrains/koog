package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.extension.toSpanEndStatus
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer

/**
 * Build and start a new Plan Creation Span with necessary attributes.
 */
internal fun startPlanCreationSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    runId: String,
    stepIndex: Int,
    state: String,
    currentPlan: String?,
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.PLAN_CREATION,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.INTERNAL,
        name = "plan creation $stepIndex",
    )
        .addAttribute(SpanAttributes.Conversation.Id(runId))
        .addAttribute(KoogAttributes.Koog.Event.Id(id))
        .addAttribute(KoogAttributes.Koog.Planner.StepIndex(stepIndex))
        .addAttribute(KoogAttributes.Koog.Planner.State(state))
        .addAttribute(KoogAttributes.Koog.Planner.Plan(currentPlan ?: "no plan yet"))

    return builder.buildAndStart(tracer)
}

/**
 * End Plan Creation Span and set final attributes.
 */
internal fun endPlanCreationSpan(
    span: GenAIAgentSpan,
    newPlan: String,
    error: Throwable? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.PLAN_CREATION) {
        "${span.logString} Expected to end span type of type: <${SpanType.PLAN_CREATION}>, but received span of type: <${span.type}>"
    }

    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    span.addAttribute(KoogAttributes.Koog.Planner.NewPlan(newPlan))

    span.end(error.toSpanEndStatus(), verbose)
}

/**
 * Build and start a new Step Execution Span with necessary attributes.
 */
internal fun startStepExecutionSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    runId: String,
    stepIndex: Int,
    state: String,
    plan: String,
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.STEP_EXECUTION,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.INTERNAL,
        name = "step execution $stepIndex",
    )
        .addAttribute(SpanAttributes.Conversation.Id(runId))
        .addAttribute(KoogAttributes.Koog.Event.Id(id))
        .addAttribute(KoogAttributes.Koog.Planner.StepIndex(stepIndex))
        .addAttribute(KoogAttributes.Koog.Planner.State(state))
        .addAttribute(KoogAttributes.Koog.Planner.Plan(plan))

    return builder.buildAndStart(tracer)
}

/**
 * End Step Execution Span and set final attributes.
 */
internal fun endStepExecutionSpan(
    span: GenAIAgentSpan,
    state: String,
    error: Throwable? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.STEP_EXECUTION) {
        "${span.logString} Expected to end span type of type: <${SpanType.STEP_EXECUTION}>, but received span of type: <${span.type}>"
    }

    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    span.addAttribute(KoogAttributes.Koog.Planner.NewState(state))

    span.end(error.toSpanEndStatus(), verbose)
}

/**
 * Build and start a new Plan Completion Evaluation Span with necessary attributes.
 */
internal fun startPlanCompletionEvaluationSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    runId: String,
    stepIndex: Int,
    state: String,
    plan: String,
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.PLAN_COMPLETION_EVALUATION,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.INTERNAL,
        name = "plan completion evaluation $stepIndex",
    )
        .addAttribute(SpanAttributes.Conversation.Id(runId))
        .addAttribute(KoogAttributes.Koog.Event.Id(id))
        .addAttribute(KoogAttributes.Koog.Planner.StepIndex(stepIndex))
        .addAttribute(KoogAttributes.Koog.Planner.State(state))
        .addAttribute(KoogAttributes.Koog.Planner.Plan(plan))

    return builder.buildAndStart(tracer)
}

/**
 * End Plan Completion Evaluation Span and set final attributes.
 */
internal fun endPlanCompletionEvaluationSpan(
    span: GenAIAgentSpan,
    isCompleted: Boolean,
    error: Throwable? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.PLAN_COMPLETION_EVALUATION) {
        "${span.logString} Expected to end span type of type: <${SpanType.PLAN_COMPLETION_EVALUATION}>, but received span of type: <${span.type}>"
    }

    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    span.addAttribute(KoogAttributes.Koog.Planner.IsCompleted(isCompleted))

    span.end(error.toSpanEndStatus(), verbose)
}
