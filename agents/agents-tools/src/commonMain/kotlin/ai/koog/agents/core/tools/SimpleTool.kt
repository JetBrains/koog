package ai.koog.agents.core.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Represents a simplified tool base class that processes specific arguments and produces a textual result.
 *
 * @param TArgs The type of arguments the tool accepts.
 */
public abstract class SimpleTool<TArgs> : Tool<TArgs, String>() {
    override fun encodeResultToString(result: String): String = result

    /**
     * Deprecated in favor of `String`.
     */
    @Deprecated("String directly")
    public fun encodeResultToString(result: ToolResult.Text): String = result.text
    override val resultSerializer: KSerializer<String> = String.serializer()

    final override suspend fun execute(args: TArgs): String = doExecute(args)

    /**
     * Executes the tool's main functionality using the provided arguments and produces a textual result.
     *
     * @param args The arguments of type [TArgs] required to perform the execution.
     * @return A string representing the result of the execution.
     */
    public abstract suspend fun doExecute(args: TArgs): String
}
