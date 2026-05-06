package ai.koog.agents.core.agent

import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import ai.koog.serialization.typeToken

/**
 * Convenience base class for [AIAgentContextAwareTool]s that return a plain [String]. Mirrors
 * [ai.koog.agents.core.tools.SimpleTool] — overrides [encodeResultToString] so the raw string is
 * passed back to the LLM without JSON wrapping.
 *
 * @param TArgs The type of the tool's arguments.
 */
public abstract class SimpleContextAwareTool<TArgs>(
    argsType: TypeToken,
    name: String,
    description: String,
) : AIAgentContextAwareTool<TArgs, String>(
    argsType = argsType,
    resultType = typeToken<String>(),
    name = name,
    description = description,
) {
    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result
}
