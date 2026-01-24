package ai.koog.agents.tools.test.utils

import ai.koog.agents.core.tools.Tool
import kotlinx.coroutines.runBlocking

object ToolUtils {
    @JvmStatic
    fun <A, R> executeToolBlocking(tool: Tool<A, R>, arg: A): R = runBlocking { tool.execute(arg) }
}
