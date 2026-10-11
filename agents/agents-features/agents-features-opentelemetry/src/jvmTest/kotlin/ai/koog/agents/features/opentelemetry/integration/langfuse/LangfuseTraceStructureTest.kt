package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.integration.TraceStructureTestBase
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * A test class for verifying trace structures using the Langfuse exporter.
 */
@EnabledIfEnvironmentVariable(named = "LANGFUSE_SECRET_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LANGFUSE_HOST", matches = ".+")
class LangfuseTraceStructureTest :
    TraceStructureTestBase(openTelemetryConfigurator = { addLangfuseExporter() }) {

    override fun expectedTextExchangeAttributes(systemPrompt: String, userPrompt: String, answer: String): Map<String, Any> =
        mapOf(
            "gen_ai.prompt" to
                """[{"role":"system","content":${JsonPrimitive(systemPrompt)}},{"role":"user","content":${JsonPrimitive(userPrompt)}}]""",
            "gen_ai.completion" to """[{"role":"assistant","content":${JsonPrimitive(answer)}}]""",
        )

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
            "gen_ai.response.finish_reasons" to listOf(GenAIAttributes.Response.FinishReasonType.ToolCalls.id),
            "gen_ai.prompt" to """[{"role":"system","content":"$systemPrompt"},{"role":"user","content":"$userPrompt"}]""",
            "gen_ai.completion" to
                """[{"role":"assistant","tool_calls":[{"id":"$toolCallId","type":"function","function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"Paris\"}"}}],"finish_reason":"${GenAIAttributes.Response.FinishReasonType.ToolCalls.id}"}]""",
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
                Message.User(
                    parts = listOf(MessagePart.Tool.Result(id = toolCallId, tool = TestGetWeatherTool.name, output = toolResponse)),
                    metaInfo = RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())
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
            "gen_ai.response.finish_reasons" to listOf(GenAIAttributes.Response.FinishReasonType.Stop.id),
            "gen_ai.prompt" to
                """[{"role":"system","content":"$systemPrompt"},{"role":"user","content":"$userPrompt"},{"role":"assistant","tool_calls":[{"id":"$toolCallId","type":"function","function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"Paris\"}"}}]},{"role":"tool","tool_call_id":"$toolCallId","content":"$toolResponse"}]""",
            "gen_ai.completion" to """[{"role":"assistant","content":"$finalResponse"}]""",
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
            "gen_ai.response.finish_reasons" to listOf(GenAIAttributes.Response.FinishReasonType.ToolCalls.id),

            "gen_ai.prompt" to """[{"role":"system","content":"$systemPrompt"},{"role":"user","content":"$userPrompt"}]""",
            "gen_ai.completion" to
                """[{"role":"assistant","tool_calls":[{"id":"$toolCallId","type":"function","function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"Paris\"}"}}],"finish_reason":"${GenAIAttributes.Response.FinishReasonType.ToolCalls.id}"}]""",
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
                Message.User(
                    parts = listOf(MessagePart.Tool.Result(id = toolCallId, tool = TestGetWeatherTool.name, output = toolResponse)),
                    metaInfo = RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())
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
            "gen_ai.response.finish_reasons" to listOf(GenAIAttributes.Response.FinishReasonType.Stop.id),

            "gen_ai.prompt" to
                """[{"role":"system","content":"$systemPrompt"},{"role":"user","content":"$userPrompt"},{"role":"assistant","tool_calls":[{"id":"$toolCallId","type":"function","function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"Paris\"}"}}]},{"role":"tool","tool_call_id":"$toolCallId","content":"$toolResponse"}]""",
            "gen_ai.completion" to """[{"role":"assistant","content":"$finalResponse"}]""",
        )
    }
}
