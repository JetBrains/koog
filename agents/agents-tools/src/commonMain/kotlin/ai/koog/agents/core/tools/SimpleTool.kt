package ai.koog.agents.core.tools

import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import ai.koog.serialization.typeToken

/**
 * Represents a simplified tool base class that processes specific arguments and produces a textual result.
 *
 * @param TArgs The type of arguments the tool accepts.
 */
public abstract class SimpleTool<TArgs>(
    argsType: TypeToken,
    name: String,
    description: String,
) : Tool<TArgs, String>(
    argsType = argsType,
    resultType = typeToken<String>(),
    name = name,
    description = description,
) {
    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result
}
