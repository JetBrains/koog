package ai.koog.agents.core.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Represents a simplified tool base class that processes specific arguments and produces a textual result.
 *
 * @param TArgs The type of arguments the tool accepts.
 */
public abstract class SimpleTool<TArgs>(
    argsSerializer: KSerializer<TArgs>,
    name: String,
    description: String,
) : Tool<TArgs, String>(
    argsSerializer = argsSerializer,
    resultSerializer = String.serializer(),
    name = name,
    description = description,
) {
    override fun encodeResultToString(result: String): String = result
}
