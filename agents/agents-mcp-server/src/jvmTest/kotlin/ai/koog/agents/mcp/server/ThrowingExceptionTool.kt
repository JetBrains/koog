package ai.koog.agents.mcp.server

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.testing.tools.RandomNumberTool
import kotlinx.io.IOException
import kotlinx.serialization.KSerializer

internal class ThrowingExceptionTool : Tool<RandomNumberTool.Args, ToolResult.Number>() {

    private val tool = RandomNumberTool()

    var last: Result<ToolResult.Number>? = null
    var throwing: Boolean = false

    override val argsSerializer: KSerializer<RandomNumberTool.Args> = RandomNumberTool.Args.serializer()
    override val descriptor: ToolDescriptor = tool.descriptor

    @OptIn(InternalAgentToolsApi::class)
    override suspend fun execute(args: RandomNumberTool.Args): ToolResult.Number {
        return runCatching {
            if (throwing) {
                throw IOException("Can not do something during IO")
            } else {
                tool.execute(args, object : DirectToolCallsEnabler {})
            }
        }
            .also { last = it }
            .getOrThrow()
    }
}
