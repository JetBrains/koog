package ai.koog.agents.mcp.server

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.serialization.typeToken
import kotlinx.io.IOException

internal class ThrowingExceptionTool : Tool<RandomNumberTool.Args, Int>(
    argsType = typeToken<RandomNumberTool.Args>(),
    resultType = typeToken<Int>(),
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
