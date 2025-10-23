package ai.koog.agents.mcp.server

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.testing.tools.RandomNumberTool
import kotlinx.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

internal class ThrowingExceptionTool : Tool<RandomNumberTool.Args, Int>() {

    private val tool = RandomNumberTool()

    var last: Result<Int>? = null
    var throwing: Boolean = false

    override val argsSerializer: KSerializer<RandomNumberTool.Args> = RandomNumberTool.Args.serializer()
    override val resultSerializer: KSerializer<Int> = Int.serializer()
    override val name = tool.name
    override val description: String = tool.description

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
