package ai.koog.agents.core.tools

/**
 * Represents a simplified tool base class that processes specific arguments and produces a textual result.
 *
 * @param TArgs The type of arguments the tool accepts, which must be a subtype of `Tool.Args`.
 */
public abstract class SimpleTool<TArgs : Tool.Args> : Tool<TArgs, ToolResult.Text>() {
    override fun encodeResultToString(result: ToolResult.Text): String = result.text

    final override suspend fun execute(args: TArgs): ToolResult.Text = ToolResult.Text(doExecute(args))

    /**
     * Executes the tool's main functionality using the provided arguments and produces a textual result.
     *
     * @param args The arguments of type [TArgs] required to perform the execution.
     * @return A string representing the result of the execution.
     */
    public abstract suspend fun doExecute(args: TArgs): String
}
