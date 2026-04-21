package ai.koog.agents.mcp.server

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.testing.tools.RandomNumberTool
import kotlinx.io.IOException
import kotlinx.serialization.builtins.serializer

internal class ThrowingExceptionTool : Tool<RandomNumberTool.Args, Int>(
    argsSerializer = RandomNumberTool.Args.serializer(),
    resultSerializer = Int.serializer(),
    name = RandomNumberTool().name,
    description = RandomNumberTool().descriptor.description
) {
    private val tool = RandomNumberTool()

    var last: Result<Int>? = null
    var throwing: Boolean = false

    @OptIn(InternalAgentToolsApi::class)
    override suspend fun execute(args: RandomNumberTool.Args): Int {
        return runCatching {
            if (throwing) {
                throw IOException("Can not do something during IO")
            } else {
                tool.execute(args)
            }
        }
            .also { last = it }
            .getOrThrow()
    }
}
