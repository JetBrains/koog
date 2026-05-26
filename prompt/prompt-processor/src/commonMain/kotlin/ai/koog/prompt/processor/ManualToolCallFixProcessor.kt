package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONSerializer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A response processor that fixes invalid tool call jsons.
 * Fixes incorrectly formatted jsons, e.g.
 *   - incorrect tool id / name / arguments keys
 *   - missing escapes in strings
 *
 * @param toolRegistry The tool registry with available tools
 * @param toolCallJsonConfig Configuration for parsing and fixing tool call json
 */
public class ManualToolCallFixProcessor @JvmOverloads constructor(
    toolRegistry: ToolRegistry,
    toolCallJsonConfig: ToolCallJsonConfig = ToolCallJsonConfig()
) : ToolJsonFixProcessor(toolRegistry, toolCallJsonConfig) {

    private companion object {
        @JvmStatic
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun process(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        response: Message.Assistant,
        serializer: JSONSerializer,
    ): Message.Assistant {
        return Message.Assistant(
            parts = response.parts.map { part ->
                when (part) {
                    is MessagePart.Tool.Call -> extractToolCall(part.args) ?: part
                    is MessagePart.Text -> extractToolCall(part.text) ?: part
                    else -> part
                }
            },
            finishReason = response.finishReason,
            metaInfo = response.metaInfo
        )
    }
}
