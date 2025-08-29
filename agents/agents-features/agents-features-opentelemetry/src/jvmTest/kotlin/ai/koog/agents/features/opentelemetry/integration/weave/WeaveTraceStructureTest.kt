package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.integration.TraceStructureTestBase
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * A test class for verifying trace structures using the Weave exporter.
 */
@EnabledIfEnvironmentVariable(named = "WEAVE_ENTITY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WEAVE_API_KEY", matches = ".+")
class WeaveTraceStructureTest :
    TraceStructureTestBase(openTelemetryConfigurator = { addWeaveExporter() }) {

    override fun testLLMCallToolCallLLMCallGetExpectedInitialLLMCallSpanAttributes(
        model: LLModel,
        temperature: Double,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String
    ): Map<String, Any> = mapOf(
        "gen_ai.system" to model.provider.id,
        "gen_ai.conversation.id" to runId,
        "gen_ai.operation.name" to "chat",
        "gen_ai.request.temperature" to temperature,
        "gen_ai.request.model" to model.id,
        "gen_ai.response.finish_reasons" to listOf(SpanAttributes.Response.FinishReasonType.ToolCalls.id),

        "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
        "gen_ai.prompt.0.content" to systemPrompt,
        "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
        "gen_ai.prompt.1.content" to userPrompt,

        "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
        "gen_ai.completion.0.tool_calls.0.id" to toolCallId,
        "gen_ai.completion.0.tool_calls.0.function" to "{\"name\":\"${TestGetWeatherTool.name}\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"}",
        "gen_ai.completion.0.tool_calls.0.type" to "function",
        "gen_ai.completion.0.finish_reason" to SpanAttributes.Response.FinishReasonType.ToolCalls.id,
    )

    override fun testLLMCallToolCallLLMCallGetExpectedFinalLLMCallSpansAttributes(
        model: LLModel,
        temperature: Double,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String,
        toolResponse: String,
        finalResponse: String,
    ): Map<String, Any> = mapOf(
        "gen_ai.system" to model.provider.id,
        "gen_ai.conversation.id" to runId,
        "gen_ai.operation.name" to "chat",
        "gen_ai.request.temperature" to temperature,
        "gen_ai.request.model" to model.id,
        "gen_ai.response.finish_reasons" to listOf(SpanAttributes.Response.FinishReasonType.Stop.id),

        "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
        "gen_ai.prompt.0.content" to systemPrompt,
        "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
        "gen_ai.prompt.1.content" to userPrompt,
        "gen_ai.prompt.2.role" to Message.Role.Assistant.name.lowercase(),
        "gen_ai.prompt.2.tool_calls.0.type" to "function",
        "gen_ai.prompt.2.tool_calls.0.id" to toolCallId,
        "gen_ai.prompt.2.tool_calls.0.function" to "{\"name\":\"Get whether\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"}",
        "gen_ai.prompt.2.finish_reason" to "tool_calls",
        "gen_ai.prompt.3.role" to Message.Role.Tool.name.lowercase(),
        "gen_ai.prompt.3.tool_call_id" to toolCallId,
        "gen_ai.prompt.3.content" to toolResponse,

        "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
        "gen_ai.completion.0.content" to finalResponse,
    )
}
