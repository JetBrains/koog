package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.serialization.*
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.jvmName

private const val nonSerializableParameterPrefix = "__##nonSerializableParameter##__"

/**
 * A tool implementation that wraps a Kotlin callable (function, method, etc.).
 *
 * @see [asTool]
 * @see [asTools]
 *
 * @property callable The Kotlin callable (KFunction or KProperty) to be wrapped and executed by this tool.
 * @property thisRef An optional instance reference required if the callable is non-static.
 * @property descriptor Metadata about the tool including its name, description, and parameters.
 * @property json A JSON serializer for serializing and deserializing data.
 */
@OptIn(InternalAgentToolsApi::class)
public class ToolFromCallable(
    private val callable: KCallable<*>,
    private val thisRef: Any? = null,
    override val descriptor: ToolDescriptor,
    private val json: Json = Json,
) : Tool<ToolFromCallable.VarArgs, ToolFromCallable.Result>() {

    public data class VarArgs(val args: Map<KParameter, Any?>) : Args {
        public fun asNamedValues(): List<Pair<String, Any?>> = args.mapNotNull { (parameter, value) -> parameter.name?.let { it to value } }
    }

    public class Result(public val result: Any?, public val type: KType, public val json: Json) : ToolResult {
        override fun toStringDefault(): String {
            return json.encodeToString(serializer(type), result)
        }
    }

    init {
        ensureValid()
    }

    private fun ensureValid() {
        for (parameter in callable.parameters) {
            when (parameter.kind) {
                KParameter.Kind.VALUE -> {
                    serializerOrNull(parameter.type) ?: throw IllegalArgumentException("Parameter '${parameter.name}' of type '${parameter.type}' is not serializable")
                }

                KParameter.Kind.INSTANCE -> {
                    if (thisRef == null) throw IllegalArgumentException("Instance parameter is null for a non-static callable")
                }
                KParameter.Kind.EXTENSION_RECEIVER -> {
                    throw IllegalArgumentException("Extension functions are not allowed")
                }
            }
        }
        serializerOrNull(callable.returnType) ?: throw SerializationException("Return type '${callable.returnType}' is not serializable")
    }

    override suspend fun execute(args: VarArgs): Result {
        val instanceParameter = callable.instanceParameter
        val argsMap = if (instanceParameter != null) {
            val thisRefToCall = thisRef ?: error("Instance parameter is null")
            args.args + (instanceParameter to thisRefToCall)
        }
        else {
            args.args
        }
        val result = callable.callSuspendBy(argsMap)
        return Result(result, callable.returnType, json)
    }

    override val argsSerializer: KSerializer<VarArgs>
        get() = VarArgsSerializer(callable)

    public class VarArgsSerializer(public val kCallable: KCallable<*>) : KSerializer<VarArgs> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor
            get() = buildClassSerialDescriptor(VarArgs::class.jvmName) {
                for ((i, parameter) in kCallable.parameters.withIndex()) {
                    val missingParameterName = "$nonSerializableParameterPrefix#$i" // `this` parameter or other non-serializable, keep name as missing
                    val name = parameter.name ?: missingParameterName
                    val parameterSerializer = serializerOrNull(parameter.type) ?: NothingSerializer()
                    element(
                        elementName = name,
                        descriptor = parameterSerializer.descriptor,
                        annotations = parameter.annotations,
                        isOptional = parameter.isOptional || name.startsWith(missingParameterName)
                    )
                }
            }

        override fun serialize(
            encoder: Encoder,
            value: VarArgs,
        ) {
            val compositeEncoder = encoder.beginStructure(descriptor)
            for ((i, parameter) in kCallable.parameters.withIndex()) {
                if (parameter.name == null) continue

                val paramValue = value.args[parameter]
                if (paramValue != null) {
                    val parameterSerializer = serializer(parameter.type)
                    compositeEncoder.encodeNullableSerializableElement(
                        descriptor,
                        i,
                        parameterSerializer,
                        paramValue
                    )
                }
            }
            compositeEncoder.endStructure(descriptor)
        }

        override fun deserialize(decoder: Decoder): VarArgs {
            val argumentMap = mutableMapOf<KParameter, Any?>()
            decoder.beginStructure(descriptor).apply {
                while (true) {
                    val parameterDecodedIndex = decodeElementIndex(descriptor)
                    if (parameterDecodedIndex == CompositeDecoder.DECODE_DONE) break
                    if (parameterDecodedIndex == CompositeDecoder.UNKNOWN_NAME) continue
                    val parameter = kCallable.parameters[parameterDecodedIndex]
                    val parameterSerializer = serializer(parameter.type)
                    val paramValue =
                        this.decodeNullableSerializableElement(descriptor, parameterDecodedIndex, parameterSerializer)
                    argumentMap[parameter] = paramValue
                }
                endStructure(descriptor)
                return VarArgs(argumentMap)
            }
        }
    }
}
