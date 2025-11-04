package ai.koog.agents.core.tools.reflect.java

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.jvmErasure

private const val nonSerializableParameterPrefix = "__##nonSerializableParameter##__"

/**
 * A tool implementation that wraps a Java Method.
 *
 * @property method The Java Method to be wrapped and executed by this tool.
 * @property thisRef An optional instance reference required if the method is non-static.
 * @property descriptor Metadata about the tool including its name, description, and parameters.
 * @property json A JSON serializer for serializing and deserializing data.
 */
@OptIn(InternalAgentToolsApi::class)
@JavaAPI
public class ToolFromJavaMethod(
    private val method: java.lang.reflect.Method,
    private val thisRef: Any? = null,
    override val descriptor: ToolDescriptor,
    override val json: Json = Json,
    override val resultSerializer: KSerializer<Any?>,
) : Tool<ToolFromJavaMethod.VarArgs, Any?>() {

    /**
     * Represents a data structure to hold arguments conforming to the Args interface.
     *
     * @property args A map of parameters to their respective values.
     * Each key is a Parameter, matched with a value which can potentially be null.
     */
    @JavaAPI
    public data class VarArgs(val args: Map<java.lang.reflect.Parameter, Any?>) {
        /**
         * Converts a map of parameters and their corresponding values into a list of pairs,
         * where each pair consists of a parameter name and its associated value.
         * Parameters without names are excluded from the resulting list.
         *
         * @return a list of pairs containing parameter names and their values. If a parameter has no name, it is ignored.
         */
        @JavaAPI
        public fun asNamedValues(): List<Pair<String, Any?>> = args.mapNotNull { (parameter, value) ->
            if (parameter.isNamePresent) {
                parameter.name to value
            } else {
                null
            }
        }
    }

    init {
        ensureValid()
    }

    private fun ensureValid() {
        // Check if non-static method has thisRef
        if (!java.lang.reflect.Modifier.isStatic(method.modifiers) && thisRef == null) {
            throw IllegalArgumentException("Instance reference is null for a non-static method")
        }

        // Validate parameters are serializable
        for (parameter in method.parameters) {
            val paramType = parameter.parameterizedType.toKType()
            serializerOrNull(paramType)
                ?: throw IllegalArgumentException(
                    "Parameter '${parameter.name}' of type '${parameter.parameterizedType}' is not serializable"
                )
        }

        // Validate return type is serializable
        val returnType = method.genericReturnType.toKType()
        serializerOrNull(returnType)
            ?: throw SerializationException("Return type '${method.genericReturnType}' is not serializable")
    }

    override suspend fun execute(args: VarArgs): Any? {
        // Convert parameter map to ordered array of arguments
        val orderedArgs = method.parameters.map { param ->
            args.args[param]
        }.toTypedArray()

        // Make method accessible if needed
        method.isAccessible = true

        // Call the method
        return if (java.lang.reflect.Modifier.isStatic(method.modifiers)) {
            method.invoke(null, *orderedArgs)
        } else {
            method.invoke(thisRef, *orderedArgs)
        }
    }

    override val argsSerializer: KSerializer<VarArgs>
        get() = VarArgsSerializer(method)

    override val name: String = descriptor.name
    override val description: String = descriptor.description

    /**
     * A serializer for the `VarArgs` class, enabling Kotlin serialization for arguments provided dynamically
     * to a Java Method. This serializer facilitates encoding and decoding of arguments
     * via their corresponding `Parameter` mappings.
     *
     * @property method A reference to the `Method` instance this serializer is associated with. The method's
     * parameters are used to generate the serialization descriptor and process argument values.
     */
    @JavaAPI
    public class VarArgsSerializer(public val method: java.lang.reflect.Method) : KSerializer<VarArgs> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor
            get() = buildClassSerialDescriptor("ai.koog.agents.core.tools.reflect.ToolFromJavaMethod.VarArgs") {
                for ((i, parameter) in method.parameters.withIndex()) {
                    val name = parameter.name
                    val paramType = parameter.parameterizedType.toKType()
                    val parameterSerializer = serializer(paramType)
                    element(
                        elementName = name,
                        descriptor = parameterSerializer.descriptor,
                        annotations = parameter.annotations.toList(),
                        isOptional = false // Java doesn't have optional params
                    )
                }
            }

        override fun serialize(
            encoder: Encoder,
            value: VarArgs,
        ) {
            val compositeEncoder = encoder.beginStructure(descriptor)
            for ((i, parameter) in method.parameters.withIndex()) {
                if (!parameter.isNamePresent) continue

                val paramValue = value.args[parameter]
                if (paramValue != null) {
                    val paramType = parameter.parameterizedType.toKType()
                    val parameterSerializer = serializer(paramType)
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
            val argumentMap = mutableMapOf<java.lang.reflect.Parameter, Any?>()
            decoder.beginStructure(descriptor).apply {
                while (true) {
                    val parameterDecodedIndex = decodeElementIndex(descriptor)
                    if (parameterDecodedIndex == CompositeDecoder.DECODE_DONE) break
                    if (parameterDecodedIndex == CompositeDecoder.UNKNOWN_NAME) continue
                    val parameter = method.parameters[parameterDecodedIndex]
                    val paramType = parameter.parameterizedType.toKType()
                    val parameterSerializer = serializer(paramType)
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

// Converts a Java java.lang.reflect.Type into a Kotlin KType suitable for kotlinx.serialization
private fun java.lang.reflect.Type.toKType(): KType {
    return when (this) {
        is Class<*> -> {
            if (this.isArray) {
                // For arrays, rely on kotlin reflection class for this array type
                return this.kotlin.createType(nullable = true)
            } else {
                val kClass = this.kotlin
                val nullable = !this.isPrimitive
                kClass.createType(nullable = nullable)
            }
        }
        is java.lang.reflect.ParameterizedType -> {
            val raw = (this.rawType as Class<*>).kotlin
            val projections = this.actualTypeArguments.map {
                KTypeProjection.invariant(it.toKType())
            }
            // Parameterized types are reference types -> nullable
            raw.createType(projections, nullable = true)
        }
        is java.lang.reflect.GenericArrayType -> {
            val component = this.genericComponentType.toKType()
            kotlin.Array<Any?>::class.createType(listOf(KTypeProjection.invariant(component)), nullable = true)
        }
        is java.lang.reflect.WildcardType -> {
            // Use upper bound if present, else Any?
            val upper = this.upperBounds.firstOrNull()
            (upper ?: Any::class.java).toKType()
        }
        is java.lang.reflect.TypeVariable<*> -> {
            // Use first bound or Any?
            val bound = this.bounds.firstOrNull() ?: Any::class.java
            bound.toKType()
        }
        else -> Any::class.createType(nullable = true)
    }
}
