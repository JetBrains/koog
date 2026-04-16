package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.schema.getToolDescriptor
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.KotlinTypeToken
import ai.koog.serialization.typeToken
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.valueParameters

/**
 * A tool implementation that wraps a Kotlin callable (function, method, etc.).
 *
 * @param callable The Kotlin callable (KFunction or KProperty) to be wrapped and executed by this tool.
 * @param thisRef An optional instance reference required if the callable is non-static.
 * @param name The name of the tool. If not provided, the name of the callable will be used.
 * @param description The description of the tool. If not provided, the description from the [ai.koog.agents.core.tools.annotations.LLMDescription]
 * annotation on the callable will be used.
 */
@OptIn(InternalAgentToolsApi::class)
public class ToolFromCallable<TResult>(
    public val callable: KCallable<TResult>,
    public val thisRef: Any? = null,
    name: String? = null,
    description: String? = null,
) : Tool<ToolFromCallable.Args, TResult>(
    argsType = typeToken<JSONObject>(),
    resultType = KotlinTypeToken(callable.returnType),
    descriptor = getToolDescriptor(
        callable = callable,
        toolName = name,
        toolDescription = description,
    )
) {
    /**
     * Arguments for [ToolFromCallable].
     *
     * @property parameters A map of [ToolFromCallable.callable] parameter names to their values.
     */
    public class Args(
        public val parameters: Map<KParameter, Any?>
    )

    private val valueParameterTypes = callable.valueParameters
        .associateWith { KotlinTypeToken(it.type) }

    override fun decodeArgs(rawArgs: JSONObject, serializer: JSONSerializer): Args {
        val parametersMap = buildMap {
            valueParameterTypes.forEach { (param, paramType) ->
                rawArgs.entries[param.nameOrThrow]?.let { arg ->
                    put(param, serializer.decodeFromJSONElement<Any?>(arg, paramType))
                }
            }
        }

        return Args(parametersMap)
    }

    override fun decodeResult(rawResult: JSONElement, serializer: JSONSerializer): TResult {
        return serializer.decodeFromJSONElement(rawResult, resultType)
    }

    override fun encodeArgs(args: Args, serializer: JSONSerializer): JSONObject {
        val entriesMap = buildMap {
            args.parameters.forEach { (param, paramValue) ->
                valueParameterTypes[param]?.let { paramType ->
                    put(param.nameOrThrow, serializer.encodeToJSONElement(paramValue, paramType))
                }
            }
        }

        return JSONObject(entriesMap)
    }

    override fun encodeResult(result: TResult, serializer: JSONSerializer): JSONElement {
        return serializer.encodeToJSONElement(result, resultType)
    }

    override suspend fun execute(args: Args): TResult {
        val argsMap = buildMap {
            callable.instanceParameter?.let { instanceParam ->
                put(instanceParam, requireNotNull(thisRef) { "This ref must be not null for a non-static callable" })
            }

            putAll(args.parameters)
        }

        return callable.callSuspendBy(argsMap)
    }
}

private val KParameter.nameOrThrow: String
    get() = name ?: throw IllegalArgumentException("Callable parameter must have a name, but got null: $this")
