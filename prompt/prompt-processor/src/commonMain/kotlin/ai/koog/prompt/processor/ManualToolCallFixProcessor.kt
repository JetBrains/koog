package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
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
public class ManualToolCallFixProcessor(
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
        responses: List<Message.Response>
    ): List<Message.Response> = responses.map { response ->
        logger.info { "Updating message: $response" }
        (
            response
                as? Message.Tool.Call
                ?: extractToolCall(response.content, response.metaInfo)
                ?: response
            )
            .also { logger.info { "Updated message: $it" } }
    }
}
