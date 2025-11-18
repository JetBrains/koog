package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.serialization.ToolJson
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter

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
    override val json: Json = ToolJson,
    override val resultSerializer: KSerializer<Any?>,
) : Tool<ToolFromCallable.VarArgs, Any?>() {

    /**
     * Represents a data structure to hold arguments conforming to the Args interface.
     *
     * @property args A map of parameters to their respective values.
     * Each key is a KParameter, matched with a value which can potentially be null.
     */
    public data class VarArgs(val args: Map<KParameter, Any?>) {
        /**
         * Converts a map of parameters and their corresponding values into a list of pairs,
         * where each pair consists of a parameter name and its associated value.
         * Parameters without names are excluded from the resulting list.
         *
         * @return a list of pairs containing parameter names and their values. If a parameter has no name, it is ignored.
         */
        public fun asNamedValues(): List<Pair<String, Any?>> = args.mapNotNull { (parameter, value) ->
            parameter.name?.let {
                it to value
            }
        }
    }

    init {
        ensureValid()
    }

    private fun ensureValid() {
        for (parameter in callable.parameters) {
            when (parameter.kind) {
                KParameter.Kind.VALUE -> {
                    serializerOrNull(parameter.type)
                        ?: throw IllegalArgumentException(
                            "Parameter '${parameter.name}' of type '${parameter.type}' is not serializable"
                        )
                }

                KParameter.Kind.INSTANCE -> {
                    if (thisRef ==
                        null
                    ) {
                        throw IllegalArgumentException("Instance parameter is null for a non-static callable")
                    }
                }

                KParameter.Kind.EXTENSION_RECEIVER -> {
                    throw IllegalArgumentException("Extension functions are not allowed")
                }
            }
        }
        serializerOrNull(callable.returnType)
            ?: throw SerializationException("Return type '${callable.returnType}' is not serializable")
    }

    override suspend fun execute(args: VarArgs): Any? {
        val instanceParameter = callable.instanceParameter
        val argsMap = if (instanceParameter != null) {
            val thisRefToCall = thisRef ?: error("Instance parameter is null")
            args.args + (instanceParameter to thisRefToCall)
        } else {
            args.args
        }
        return callable.callSuspendBy(argsMap)
    }

    override val argsSerializer: KSerializer<VarArgs>
        get() = VarArgsSerializer(callable)

    override val name: String = descriptor.name
    override val description: String = descriptor.description

    /**
     * A serializer for the `VarArgs` class, enabling Kotlin serialization for arguments provided dynamically
     * to a callable function (`KCallable`). This serializer facilitates encoding and decoding of arguments
     * via their corresponding `KParameter` mappings.
     *
     * @property kCallable A reference to the `KCallable` instance this serializer is associated with. The callable's
     * parameters are used to generate the serialization descriptor and process argument values.
     */
    public class VarArgsSerializer(public val kCallable: KCallable<*>) : KSerializer<VarArgs> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor
            get() = buildClassSerialDescriptor(" ai.koog.agents.core.tools.reflect.ToolFromCallable.VarArgs") {
                for ((i, parameter) in kCallable.parameters.withIndex()) {
                    val missingParameterName =
                        "$nonSerializableParameterPrefix#$i" // `this` parameter or other non-serializable, keep name as missing
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
