package ai.koog.agents.core.tools.reflect.java

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ParamInfo
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.core.tools.reflect.asToolType
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.kotlinFunction

/**
 * Converts this [ToolSet] instance into a list of [Tool]s by reflecting on its functions.
 *
 * @param json The Json instance to use for serialization. Defaults to [Json].
 * @return A list of [Tool] objects representing the tools defined by this [ToolSet].
 */
@JavaAPI
public fun ToolSet.asJavaTools(json: Json = Json): List<ai.koog.agents.core.tools.Tool<*, *>> {
    return this::class.java.asJavaTools(json = json, thisRef = this)
}

/**
 * Converts the methods of the given class, which implements `ToolSet`, into a list of tools.
 *
 * This function uses reflection to locate methods annotated with a tool-related annotation
 * and transforms them into instances of `Tool`. The conversion leverages the provided JSON format
 * and the optional reference to the toolset instance.
 *
 * @param json The `Json` instance used for serialization and deserialization during tool creation. Defaults to `Json`.
 * @param thisRef An optional reference to an instance of the toolset class. It is used as the receiver for the methods converted into tools.
 * @return A list of `Tool` objects created from the annotated methods of the class.
 * @throws IllegalArgumentException if no annotated methods are found in the class.
 */
@OptIn(InternalAgentToolsApi::class)
@JavaAPI
public fun <T : ToolSet> Class<out T>.asJavaTools(
    json: Json = Json,
    thisRef: T? = null
): List<ai.koog.agents.core.tools.Tool<*, *>> {
    return this.methods.filter { m ->
        m.getPreferredToolAnnotation() != null
    }.map {
        it.asTool(json = json, thisRef = thisRef)
    }.apply {
        require(isNotEmpty()) { "No tools found in ${this@asJavaTools}" }
    }
}

/**
 * Converts a Java `Method` into a `Tool` representation for use within the agent framework.
 *
 * This function attempts to convert the provided `Method` to a `KFunction` if it is a Kotlin function;
 * otherwise, it creates a `Tool` based on Java reflection metadata.
 *
 * @param json The JSON format to use for serializing and deserializing arguments and results. Defaults to the standard `Json` instance.
 * @param thisRef The instance on which the method will be invoked. This is required for non-static methods.
 * @param name An optional name for the tool. If not provided, one will be generated.
 * @param description An optional description of the tool's functionality. If not provided, it will be inferred.
 * @return A tool instance derived from the given `Method`, capable of being executed by the agent framework.
 */
@InternalAgentToolsApi
public fun java.lang.reflect.Method.asTool(
    json: Json = Json,
    thisRef: Any? = null,
    name: String? = null,
    description: String? = null
): ai.koog.agents.core.tools.Tool<ToolFromJavaMethod.VarArgs, Any?> {
    // build tool descriptor from Java reflection
    val toolDescriptor = this.asToolDescriptor(name = name, description = description)

    return ToolFromJavaMethod(
        method = this,
        thisRef = thisRef,
        descriptor = toolDescriptor,
        json = json,
        resultSerializer = serializer(returnType.kotlin.createType())
    )
}

@InternalAgentToolsApi
@JavaAPI
internal fun Method.asToolDescriptor(
    name: String? = null,
    description: String? = null
): ai.koog.agents.core.tools.ToolDescriptor {
    val toolName = name
        ?: this.getAnnotation(Tool::class.java)?.customName?.ifBlank { this.name }
        ?: this.name
    val toolDescription = description
        ?: this.getPreferredToolDescriptionAnnotation()?.description
        ?: this.name

    val toolParameters = this.parameters.mapNotNull { param ->
        val parameterName = param.getParameterName() ?: return@mapNotNull null
        val toolParameterDescription =
            param.getPreferredParameterDescriptionAnnotation(this)?.description ?: parameterName
        val paramType = param.type
        val paramToolType = paramType.asToolType()
        val isOptional = false
        val parameterDescriptor = ToolParameterDescriptor(
            name = parameterName,
            type = paramToolType,
            description = toolParameterDescription
        )
        ParamInfo(descriptor = parameterDescriptor, isOptional = isOptional)
    }

    return ToolDescriptor(
        name = toolName,
        description = toolDescription,
        requiredParameters = toolParameters.filter { !it.isOptional }.map { it.descriptor },
        optionalParameters = toolParameters.filter { it.isOptional }.map { it.descriptor }
    )
}

/**
 * Retrieves the preferred `LLMDescription` annotation for the current function, if available.
 * The preferred annotation is determined by checking if the annotation exists directly on
 * the function itself or by evaluating other related methods through `getPreferredToolDescriptionAndMethod`.
 *
 * @return The `LLMDescription` annotation associated with the function, or `null` if no such annotation is found.
 */
private fun Method.getPreferredToolDescriptionAnnotation(): LLMDescription? {
    return getPreferredToolDescriptionAndMethod()?.second
}

/**
 * Finds and returns the preferred `LLMDescription` annotation along with the corresponding `KFunction`.
 * The function prioritizes the annotation defined directly on the current function.
 * If no annotation is found on the current function, it searches the implemented methods for a tool method annotated with `LLMDescription`.
 *
 * @return A `Pair` containing the `KFunction` and `LLMDescription` annotation if found,
 * or `null` if no suitable annotation is available either on the function itself or its implemented methods.
 */
private fun Method.getPreferredToolDescriptionAndMethod(): Pair<Method, LLMDescription>? {
    // Annotation exactly on this function is preferred
    val thisAnnotation = getAnnotation(LLMDescription::class.java)
    if (thisAnnotation != null) return this to thisAnnotation

    val (toolMethod, _) = getToolMethodAndAnnotation() ?: return null
    val lLMDescriptionAnnotation = toolMethod.getAnnotation(LLMDescription::class.java) ?: return null
    return toolMethod to lLMDescriptionAnnotation
}

// For Java Method
private fun Method.getToolMethodAndAnnotation(): Pair<Method, Tool>? {
    // Annotation directly on this method is preferred
    val thisAnnotation = this.getAnnotation(Tool::class.java)
    if (thisAnnotation != null) return this to thisAnnotation

    // Check overridden/interface methods
    return getImplementedMethods().firstNotNullOfOrNull { m ->
        m.getAnnotation(Tool::class.java)?.let { m to it }
    }
}

/**
 * Retrieves the preferred `LLMDescription` annotation for a given parameter of a method.
 * It first checks if the parameter itself has the `LLMDescription` annotation. If not,
 * it searches for the corresponding parameter in the annotated tool method, if available,
 * and retrieves its `LLMDescription` annotation.
 *
 * @param method The function in which the current parameter is contained.
 * @return The `LLMDescription` annotation associated with the parameter, if one exists; otherwise, null.
 */
private fun Parameter.getPreferredParameterDescriptionAnnotation(method: Method): LLMDescription? {
    val thisParameterDescription = getAnnotation(LLMDescription::class.java)
    if (thisParameterDescription != null) return thisParameterDescription
    return null
}

@InternalAgentToolsApi
@JavaAPI
public fun Parameter.getParameterName(): String? {
    if (isNamePresent) return name
    val method = declaringExecutable as? Method ?: return name
    val kFunction = try {
        method.kotlinFunction
    } catch (e: Throwable) {
        null
    }
    if (kFunction != null) {
        val valueParameters = kFunction.parameters.filter { it.kind == kotlin.reflect.KParameter.Kind.VALUE }
        val indexInJava = method.parameters.indexOf(this)
        if (indexInJava in valueParameters.indices) {
            val kName = valueParameters[indexInJava].name
            if (kName != null && !kName.startsWith("arg")) return kName
        }
    }

    // fallback to LLMDescription if present on the parameter
    val description = getAnnotation(LLMDescription::class.java)?.description
    if (description != null && description.split(" ").size == 1 && description.all { it.isLetterOrDigit() || it == '_' }) {
        return description
    }

    return name
}

private fun Method.getPreferredToolAnnotation(): Tool? {
    return getToolMethodAndAnnotation()?.second
}

/**
 * Retrieves a sequence of methods that are implemented by the current Java method within its class hierarchy.
 *
 * The sequence includes all methods that are overridden by the current method, traversing through
 * the class and interface hierarchy of the declaring class of this method. It skips any methods
 * that are identical to the current method.
 *
 * @return A sequence of Java methods implemented by the current method, traversing its class and interface hierarchy.
 */
private fun Method.getImplementedMethods(): Sequence<Method> {
    return sequence {
        val methodName = this@getImplementedMethods.name
        val parameterTypes = this@getImplementedMethods.parameterTypes
        val visited = mutableSetOf<Class<*>>()
        val queue = ArrayDeque<Class<*>>()

        queue.add(this@getImplementedMethods.declaringClass)

        while (queue.isNotEmpty()) {
            val currentClass = queue.removeFirstOrNull() ?: break
            if (!visited.add(currentClass)) continue

            // Check superclass
            currentClass.superclass?.let { superclass ->
                if (!visited.contains(superclass)) {
                    queue.addLast(superclass)
                }
            }

            // Check interfaces
            for (iface in currentClass.interfaces) {
                if (!visited.contains(iface)) {
                    queue.add(iface)
                }
            }

            try {
                val method = currentClass.getDeclaredMethod(methodName, *parameterTypes)
                if (method != this@getImplementedMethods) yield(method)
            } catch (_: NoSuchMethodException) {
                // Method not found in this class/interface, continue traversal
            }
        }
    }
}

/**
 * Converts a Java reflection type (`Type`) to a corresponding `ToolParameterType`.
 *
 * The method analyzes the provided `Type` object to determine the appropriate `ToolParameterType`.
 * It supports basic types such as `String`, `Integer`, `Float`, and `Boolean`, as well as more complex
 * types like enumerations, arrays, and generic collections. For enumerations, it extracts the possible
 * enum constants, and for collections, it recursively determines the type of the items.
 *
 * @return The corresponding `ToolParameterType` for the provided `Type`.
 *         Throws an `IllegalArgumentException` or error for unsupported types.
 */
@JavaAPI
public fun java.lang.reflect.Type.asToolType(): ai.koog.agents.core.tools.ToolParameterType {
    return when (this) {
        // Primitive types and their wrappers
        String::class.java, java.lang.String::class.java -> ToolParameterType.String
        Int::class.java, java.lang.Integer::class.java, Integer.TYPE -> ToolParameterType.Integer
        Long::class.java, java.lang.Long::class.java, java.lang.Long.TYPE -> ToolParameterType.Integer
        Float::class.java, java.lang.Float::class.java, java.lang.Float.TYPE -> ToolParameterType.Float
        Double::class.java, java.lang.Double::class.java, java.lang.Double.TYPE -> ToolParameterType.Float
        Boolean::class.java, java.lang.Boolean::class.java, java.lang.Boolean.TYPE -> ToolParameterType.Boolean

        is ParameterizedType -> {
            val rawType = this.rawType as? Class<*> ?: error("Raw type is not a Class")

            when {
                // Handle List<T>
                List::class.java.isAssignableFrom(rawType) || Collection::class.java.isAssignableFrom(rawType) -> {
                    val itemType = this.actualTypeArguments.getOrNull(0)
                        ?: error("List/Collection item type is null")
                    val itemToolType = itemType.asToolType()
                    ToolParameterType.List(itemToolType)
                }

                else -> error("Unsupported parameterized type: $rawType")
            }
        }

        is Class<*> -> {
            when {
                // Handle enums
                this.isEnum -> {
                    @Suppress("UNCHECKED_CAST")
                    ToolParameterType.Enum(
                        (this as Class<out Enum<*>>).enumConstants.map { it.name }
                            .toTypedArray()
                    )
                }

                // Handle arrays
                this.isArray -> {
                    val componentType = this.componentType
                    val itemToolType = componentType.asToolType()
                    ToolParameterType.List(itemToolType)
                }

                // Handle data-like objects (POJOs with public fields/getters)
                else -> {
                    // Try to convert to Kotlin class for data class handling
                    val kotlinClass = this.kotlin
                    if (kotlinClass.isData) {
                        val properties = kotlinClass.memberProperties.map { prop ->
                            val description = prop.findAnnotation<LLMDescription>()?.description ?: prop.name
                            ToolParameterDescriptor(
                                name = prop.name,
                                description = description,
                                type = prop.returnType.asToolType() // Use Kotlin reflection
                            )
                        }
                        ToolParameterType.Object(properties)
                    } else {
                        // Fallback: treat as POJO and introspect fields/methods
                        val properties = this.asJavaObjectProperties()
                        ToolParameterType.Object(properties)
                    }
                }
            }
        }

        else -> error("Unsupported type: $this")
    }
}

/**
 * Extracts properties from a Java class by introspecting its fields and getter methods.
 */
private fun Class<*>.asJavaObjectProperties(): List<ai.koog.agents.core.tools.ToolParameterDescriptor> {
    val properties = mutableListOf<ai.koog.agents.core.tools.ToolParameterDescriptor>()

    // Extract from public fields
    this.fields.forEach { field ->
        val description = field.getAnnotation(LLMDescription::class.java)?.description ?: field.name
        properties.add(
            ai.koog.agents.core.tools.ToolParameterDescriptor(
                name = field.name,
                description = description,
                type = field.genericType.asToolType()
            )
        )
    }

    // Extract from getter methods (getXxx or isXxx)
    this.methods.forEach { method ->
        val methodName = method.name
        val propertyName = when {
            methodName.startsWith("get") && methodName.length > 3 ->
                methodName.substring(3).replaceFirstChar { it.lowercase() }

            methodName.startsWith("is") && methodName.length > 2 ->
                methodName.substring(2).replaceFirstChar { it.lowercase() }

            else -> null
        }

        if (propertyName != null &&
            method.parameterCount == 0 &&
            method.returnType != Void.TYPE &&
            properties.none { it.name == propertyName }
        ) {
            val description = method.getAnnotation(LLMDescription::class.java)?.description ?: propertyName
            properties.add(
                ToolParameterDescriptor(
                    name = propertyName,
                    description = description,
                    type = method.genericReturnType.asToolType()
                )
            )
        }
    }

    return properties
}
