package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.integration.TraceStructureTestBase
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * A test class for verifying trace structures using the Langfuse exporter.
 */
@EnabledIfEnvironmentVariable(named = "LANGFUSE_SECRET_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LANGFUSE_HOST", matches = ".+")
class LangfuseTraceStructureTest :
    TraceStructureTestBase(openTelemetryConfigurator = { addLangfuseExporter() }) {

    override fun testLLMCallToolCallLLMCallGetExpectedInitialLLMCallSpanAttributes(
        model: LLModel,
        temperature: Double,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String
    ): Map<String, Any> {
        val inputMessages = OpenTelemetryTestAPI.getMessagesString(
            listOf(
                Message.System(systemPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())),
                Message.User(userPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now()))
            )
        )

        val outputMessages = OpenTelemetryTestAPI.getMessagesString(
            listOf(
                OpenTelemetryTestAPI.toolCallMessage(toolCallId, TestGetWeatherTool.name, "{\"location\":\"Paris\"}")
            )
        )

        val systemInstructions = OpenTelemetryTestAPI.getSystemInstructionsString(
            listOf(
                systemPrompt
            )
        )

        val toolDefinitions = OpenTelemetryTestAPI.getToolDefinitionsString(
            listOf(
                TestGetWeatherTool.descriptor
            )
        )

        return mapOf(
            "gen_ai.provider.name" to model.provider.id,
            "gen_ai.conversation.id" to runId,
            "gen_ai.output.type" to "text",
            "gen_ai.operation.name" to "chat",
            "gen_ai.request.temperature" to temperature,
            "gen_ai.request.model" to model.id,
            "gen_ai.response.model" to model.id,
            "gen_ai.usage.input_tokens" to 0L,
            "gen_ai.usage.output_tokens" to 0L,
            "gen_ai.input.messages" to inputMessages,
            "system_instructions" to systemInstructions,
            "gen_ai.output.messages" to outputMessages,
            "gen_ai.tool.definitions" to toolDefinitions,
            "gen_ai.response.finish_reasons" to listOf(SpanAttributes.Response.FinishReasonType.ToolCalls.id),
            "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
            "gen_ai.prompt.0.content" to systemPrompt,
            "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
            "gen_ai.prompt.1.content" to userPrompt,
            "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
            "gen_ai.completion.0.content" to "[{\"function\":{\"name\":\"${TestGetWeatherTool.name}\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"},\"id\":\"get-weather-tool-call-id\",\"type\":\"function\"}]",
            "gen_ai.completion.0.finish_reason" to SpanAttributes.Response.FinishReasonType.ToolCalls.id,
        )
    }

    override fun testLLMCallToolCallLLMCallGetExpectedFinalLLMCallSpansAttributes(
        model: LLModel,
        temperature: Double,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String,
        toolResponse: String,
        finalResponse: String
    ): Map<String, Any> {
        val inputMessages = OpenTelemetryTestAPI.getMessagesString(
            listOf(
                Message.System(systemPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())),
                Message.User(userPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())),
                OpenTelemetryTestAPI.toolCallMessage(toolCallId, TestGetWeatherTool.name, "{\"location\":\"Paris\"}"),
                Message.Tool.Result(
                    toolCallId,
                    TestGetWeatherTool.name,
                    toolResponse,
                    RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())
                )
            )
        )

        val outputMessages = OpenTelemetryTestAPI.getMessagesString(
            listOf(
                OpenTelemetryTestAPI.assistantMessage(finalResponse)
            )
        )

        val systemInstructions = OpenTelemetryTestAPI.getSystemInstructionsString(
            listOf(
                systemPrompt
            )
        )

        val toolDefinitions = OpenTelemetryTestAPI.getToolDefinitionsString(
            listOf(
                TestGetWeatherTool.descriptor
            )
        )

        return mapOf(
            "gen_ai.provider.name" to model.provider.id,
            "gen_ai.conversation.id" to runId,
            "gen_ai.output.type" to "text",
            "gen_ai.operation.name" to "chat",
            "gen_ai.request.temperature" to temperature,
            "gen_ai.request.model" to model.id,
            "gen_ai.response.model" to model.id,
            "gen_ai.usage.input_tokens" to 0L,
            "gen_ai.usage.output_tokens" to 0L,
            "gen_ai.input.messages" to inputMessages,
            "system_instructions" to systemInstructions,
            "gen_ai.output.messages" to outputMessages,
            "gen_ai.tool.definitions" to toolDefinitions,
            "gen_ai.response.finish_reasons" to listOf(SpanAttributes.Response.FinishReasonType.Stop.id),
            "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
            "gen_ai.prompt.0.content" to systemPrompt,
            "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
            "gen_ai.prompt.1.content" to userPrompt,
            "gen_ai.prompt.2.role" to Message.Role.Tool.name.lowercase(),
            "gen_ai.prompt.2.content" to "[{\"function\":{\"name\":\"${TestGetWeatherTool.name}\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"},\"id\":\"get-weather-tool-call-id\",\"type\":\"function\"}]",
            "gen_ai.prompt.3.role" to Message.Role.Tool.name.lowercase(),
            "gen_ai.prompt.3.content" to toolResponse,
            "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
            "gen_ai.completion.0.content" to finalResponse,
        )
    }

    override fun testTokensCountAttributesGetExpectedInitialLLMCallSpanAttributes(
        model: LLModel,
        temperature: Double,
        maxTokens: Long,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String,
        outputTokens: Long,
    ): Map<String, Any> {
        val inputMessages = OpenTelemetryTestAPI.getMessagesString(
            listOf(
                Message.System(systemPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())),
                Message.User(userPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now()))
            )
        )

        val outputMessages = OpenTelemetryTestAPI.getMessagesString(
            listOf(
                OpenTelemetryTestAPI.toolCallMessage(toolCallId, TestGetWeatherTool.name, "{\"location\":\"Paris\"}")
            )
        )
        val systemInstructions = OpenTelemetryTestAPI.getSystemInstructionsString(listOf(systemPrompt))
        val toolDefinitions = OpenTelemetryTestAPI.getToolDefinitionsString(listOf(TestGetWeatherTool.descriptor))

        return mapOf(
            "gen_ai.provider.name" to model.provider.id,
            "gen_ai.conversation.id" to runId,
            "gen_ai.output.type" to "text",
            "gen_ai.operation.name" to "chat",
            "gen_ai.request.temperature" to temperature,
            "gen_ai.request.max_tokens" to maxTokens,
            "gen_ai.request.model" to model.id,
            "gen_ai.response.model" to model.id,
            "gen_ai.usage.input_tokens" to 0L,
            "gen_ai.usage.output_tokens" to outputTokens,
            "gen_ai.input.messages" to inputMessages,
            "system_instructions" to systemInstructions,
            "gen_ai.output.messages" to outputMessages,
            "gen_ai.tool.definitions" to toolDefinitions,
            "gen_ai.response.finish_reasons" to listOf(SpanAttributes.Response.FinishReasonType.ToolCalls.id),

            "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
            "gen_ai.prompt.0.content" to systemPrompt,
            "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
            "gen_ai.prompt.1.content" to userPrompt,
            "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
            "gen_ai.completion.0.content" to "[{\"function\":{\"name\":\"${TestGetWeatherTool.name}\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"},\"id\":\"get-weather-tool-call-id\",\"type\":\"function\"}]",
            "gen_ai.completion.0.finish_reason" to SpanAttributes.Response.FinishReasonType.ToolCalls.id,
        )
    }

    override fun testTokensCountAttributesGetExpectedFinalLLMCallSpansAttributes(
        model: LLModel,
        temperature: Double,
        maxTokens: Long,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String,
        toolResponse: String,
        finalResponse: String,
        outputTokens: Long,
    ): Map<String, Any> {
        val inputMessages = OpenTelemetryTestAPI.getMessagesString(
            listOf(
                Message.System(systemPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())),
                Message.User(userPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())),
                OpenTelemetryTestAPI.toolCallMessage(toolCallId, TestGetWeatherTool.name, "{\"location\":\"Paris\"}"),
                Message.Tool.Result(
                    toolCallId,
                    TestGetWeatherTool.name,
                    toolResponse,
                    RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())
                )
            )
        )
        val outputMessages = OpenTelemetryTestAPI.getMessagesString(
            listOf(
                OpenTelemetryTestAPI.assistantMessage(finalResponse)
            )
        )
        val systemInstructions = OpenTelemetryTestAPI.getSystemInstructionsString(
            listOf(
                systemPrompt
            )
        )

        val toolDefinitions = OpenTelemetryTestAPI.getToolDefinitionsString(
            listOf(
                TestGetWeatherTool.descriptor
            )
        )

        return mapOf(
            "gen_ai.provider.name" to model.provider.id,
            "gen_ai.conversation.id" to runId,
            "gen_ai.output.type" to "text",
            "gen_ai.operation.name" to "chat",
            "gen_ai.request.temperature" to temperature,
            "gen_ai.request.model" to model.id,
            "gen_ai.request.max_tokens" to maxTokens,
            "gen_ai.response.model" to model.id,
            "gen_ai.usage.input_tokens" to 0L,
            "gen_ai.usage.output_tokens" to outputTokens,
            "gen_ai.input.messages" to inputMessages,
            "system_instructions" to systemInstructions,
            "gen_ai.output.messages" to outputMessages,
            "gen_ai.tool.definitions" to toolDefinitions,
            "gen_ai.response.finish_reasons" to listOf(SpanAttributes.Response.FinishReasonType.Stop.id),

            "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
            "gen_ai.prompt.0.content" to systemPrompt,
            "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
            "gen_ai.prompt.1.content" to userPrompt,
            "gen_ai.prompt.2.role" to Message.Role.Tool.name.lowercase(),
            "gen_ai.prompt.2.content" to "[{\"function\":{\"name\":\"${TestGetWeatherTool.name}\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"},\"id\":\"get-weather-tool-call-id\",\"type\":\"function\"}]",
            "gen_ai.prompt.3.role" to Message.Role.Tool.name.lowercase(),
            "gen_ai.prompt.3.content" to toolResponse,

            "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
            "gen_ai.completion.0.content" to finalResponse,
        )
    }
}
