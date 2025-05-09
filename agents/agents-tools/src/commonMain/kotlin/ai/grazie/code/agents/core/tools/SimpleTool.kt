package ai.grazie.code.agents.core.tools

abstract class SimpleTool<TArgs : Tool.Args> : Tool<TArgs, ToolResult.Text>() {
    override fun encodeResultToString(result: ToolResult.Text): String = result.text

    final override suspend fun execute(args: TArgs): ToolResult.Text = ToolResult.Text(doExecute(args))

    abstract suspend fun doExecute(args: TArgs): String
}