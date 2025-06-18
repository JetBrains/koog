package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * Converts all instance methods of [this] class marked as [Tool] to a list of tools.
 *
 * See [asTool] for detailed description.
 *
 * @param json The Json instance to use for serialization.
 *
 * ```
 * interface MyToolsetInterface : ToolSet {
 *     @Tool
 *     @LLMDescription("My best tool")
 *     fun my_best_tool(arg1: String, arg2: Int)
 * }
 *
 * class MyToolset : MyToolsetInterface {
 *     @Tool
 *     @LLMDescription("My best tool overridden description")
 *     fun my_best_tool(arg1: String, arg2: Int) {
 *         // ...
 *     }
 *
 *     @Tool
 *     @LLMDescription("My best tool 2")
 *     fun my_best_tool_2(arg1: String, arg2: Int) {
 *          // ...
 *     }
 * }
 *
 * val myToolset = MyToolset()
 * val tools = myToolset.asTools()
 * ```
 */
public fun ToolSet.asTools(json: Json = Json): List<ToolFromCallable> {
    return this::class.asTools(json = json, thisRef = this)
}

/**
 * Converts all instance methods of class/interface [T] marked as [Tool] to a list of tools that will be called on object [this].
 *
 * Note: if you manually specify type parameter [T] that is more common type that the class of [this] object (like `derivedToolset.asTools<MyToolsetInterface>()`)
 * so only the methods of the specified generic type will be converted.
 *
 *  See [asTool] for detailed description.
 *
 * ```
 * interface MyToolsetInterface : ToolSet {
 *     @Tool
 *     @LLMDescription("My best tool")
 *     fun my_best_tool(arg1: String, arg2: Int)
 * }
 *
 * class MyToolset : MyToolsetInterface {
 *     @Tool
 *     @LLMDescription("My best tool overridden description")
 *     fun my_best_tool(arg1: String, arg2: Int) {
 *         // ...
 *     }
 *
 *     @Tool
 *     @LLMDescription("My best tool 2")
 *     fun my_best_tool_2(arg1: String, arg2: Int) {
 *          // ...
 *     }
 * }
 *
 * val myToolset = MyToolset()
 * val tools = myToolset.asToolsByInterface<MyToolsetInterface>() // only interface methods will be added
 * ```
 */
public inline fun <reified T : ToolSet> T.asToolsByInterface(json: Json = Json): List<ToolFromCallable> {
    return T::class.asTools(json = json, thisRef = this)
}

/**
 * Registers a set of tools in the `ToolRegistry` using a given [ToolSet].
 *
 * This method simplifies the process of adding multiple tools by internally converting
 * the tool set into a list of tools and registering them in the registry.
 *
 * @param toolSet The [ToolSet] containing the tools to be registered.
 * @param json The Json instance to use for serialization. Defaults to a standard `Json` instance if not provided.
 */
public fun ToolRegistry.Builder.tools(
    toolSet: ToolSet,
    json: Json = Json
) {
    tools(toolSet.asTools(json = json))
}

/**
 * Converts all functions of [this] class marked as [Tool] to a list of tools.
 *
 * @param json The Json instance to use for serialization.
 * @param thisRef an instance of [this] class to be used as the 'this' object for the callable in the case of instance methods.

 * @see [asTool]
 */
public fun <T : ToolSet> KClass<out T>.asTools(json: Json = Json, thisRef: T? = null): List<ToolFromCallable> {
    return this.functions.filter { m ->
        m.getPreferredToolAnnotation() != null
    }.map {
        it.asTool(json = json, thisRef = thisRef)
    }.apply {
        require(isNotEmpty()) { "No tools found in ${this@asTools}" }
    }
}

/**
 * Converts a KFunction into a code-engine tool that works by reflection.
 *
 *
 * The function can be annotated with [Tool] annotation where the name of the tool can be overridden.
 * If the custom name is not provided, the name of the function is used.
 *
 * The function can be annotated with [LLMDescription] annotation to provide a description of the tool.
 * If not provided, the name of the function is used as a description.
 *
 * The callable parameters can be annotated with [LLMDescription] annotation to provide a description of the parameter.
 * If not provided, the name of the parameter is used.
 *
 * If the function is a method that overrides or implements another method from a base class or an interface,
 * [Tool] annotation can be extracted from one of the base methods in the case when it's missing on this method.
 * In this case [LLMDescription]` annotation will be also extracted from the base method where [Tool] annotation was found.
 *
 * Both suspend and non-suspend functions are supported
 *
 * Default parameters are supported (calling site can omit them in the argument Json)
 *
 * @param json The Json instance to use for serialization.
 * @param thisRef The object instance to use as the 'this' object for the callable.
 * @param name The name of the tool. If not provided, the name will be obtained from [Tool.customName] property.
 * In the case of the missing attribute or empty name the name of the function is used.
 * @param description The description of the tool. If not provided, the description will be obtained from [LLMDescription.description] property.
 * In the case of the missing attribute or empty description the name of the function is used as a description.
 *
 *
 * # Note #
 * If you get the callable as a reference to an instance method like `myTools::my_best_tool`
 * you don't need to pass [thisRef] argument, but if the callable is a reference to an instance method obtained via class
 * you have to provide the proper value (`MyTools::my_best_tool`])
 *
 *
 * ```
 * class MyTools {
 *     @Tool
 *     fun my_best_tool(arg1: String, arg2: Int) {
 *         // ...
 *     }
 * }
 *
 * val myTools = MyTools()
 * val tool = myTools::my_best_tool.asTool(json = Json)
 * // or
 *
 * val tool = MyTools::my_best_tool.asTool(json = Json, thisRef = myTools)
 * ```
 */
public fun KFunction<*>.asTool(
    json: Json = Json,
    thisRef: Any? = null,
    name: String? = null,
    description: String? = null
): ToolFromCallable {
    val toolDescriptor = this.asToolDescriptor(name = name, description = description)
    if (instanceParameter != null && thisRef == null) error("Instance parameter is not null, but no 'this' object is provided")
    return ToolFromCallable(callable = this, thisRef = thisRef, descriptor = toolDescriptor, json = json)
}

/**
 * Registers a tool in the `ToolRegistry` using a given function and optional parameters
 * to customize the tool's properties.
 *
 * This method simplifies the process of adding a tool by internally converting a Kotlin function
 * into a `Tool` instance with the provided metadata.
 *
 * @param toolFunction The function to be registered as a tool. It serves as the functional implementation of the tool.
 * @param json The JSON serialization instance used for the tool. Defaults to a standard `Json` instance if not provided.
 * @param thisRef An optional reference to the class instance where the tool function is defined. Can be `null` if not applicable.
 * @param name An optional name to uniquely identify the tool in the registry. If `null`, a default name derived from the function will be used.
 * @param description An optional description of the tool functionality. Useful for documentation and explanatory purposes.
 */
public fun ToolRegistry.Builder.tool(
    toolFunction: KFunction<*>,
    json: Json = Json,
    thisRef: Any? = null,
    name: String? = null,
    description: String? = null
) {
    tool(toolFunction.asTool(json, thisRef, name, description))
}

/**
 * Converts a Kotlin reflection type (`KType`) to a corresponding `ToolParameterType`.
 *
 * The method analyzes the provided `KType` object to determine the appropriate `ToolParameterType`.
 * It supports basic types such as `String`, `Int`, `Float`, and `Boolean`, as well as more complex
 * types like enumerations and arrays. For enumerations, it extracts the possible enum constants,
 * and for arrays, it recursively determines the type of the array items.
 *
 * @return The corresponding `ToolParameterType` for the provided `KType`.
 *         Throws an `IllegalArgumentException` or error for unsupported types.
 */
public fun KType.asToolType(): ToolParameterType {
    val classifier = this.classifier
    return when (classifier) {
        String::class -> ToolParameterType.String
        Int::class -> ToolParameterType.Integer
        Float::class -> ToolParameterType.Float
        Boolean::class -> ToolParameterType.Boolean
        Long::class -> ToolParameterType.Integer
        Double::class -> ToolParameterType.Float

        List::class -> {
            val listItemType = this.arguments[0].type ?: error("List item type is null")
            val listItemToolType = listItemType.asToolType()
            ToolParameterType.List(listItemToolType)
        }

        is KClass<*> -> {
            val classJava = classifier.java
            when {
                classJava.isEnum -> {
                    @Suppress("UNCHECKED_CAST")
                    ToolParameterType.Enum((classJava as Class<Enum<*>>).enumConstants)
                }

                classJava.isArray -> {
                    val arrayItemType = this.arguments[0].type ?: error("Array item type is null")
                    val arrayItemToolType = arrayItemType.asToolType()
                    ToolParameterType.List(arrayItemToolType)
                }

                classifier.isData -> {
                    val properties = classifier.memberProperties.map { prop ->
                        val description = prop.findAnnotation<LLMDescription>()?.description ?: prop.name
                        ToolParameterDescriptor(
                            name = prop.name,
                            description = description,
                            type = prop.returnType.asToolType() // Recursive call
                        )
                    }
                    ToolParameterType.Object(properties)
                }

                else -> throw kotlin.IllegalArgumentException("Unsupported type $classifier")
            }
        }

        else -> error("Unsupported type $classifier")
    }
}

/**
 * Converts a [KFunction] into a [ToolDescriptor] that provides detailed metadata about the tool's name, description,
 * and parameter requirements. This can be used to programmatically describe and interact with a tool function.
 *
 * @param name An optional custom name for the tool. If not provided, the name is derived from annotations or the function name.
 * @param description An optional custom description for the tool. If not provided, the description is derived from annotations or the function name.
 * @return A [ToolDescriptor] containing the tool's name, description, required parameters, and optional parameters.
 */
public fun KFunction<*>.asToolDescriptor(name: String? = null, description: String? = null): ToolDescriptor {
    val toolName = name ?: this.getPreferredToolAnnotation()?.customName?.ifBlank { this.name } ?: this.name
    val toolDescription = description ?: this.getPreferredToolDescriptionAnnotation()?.description ?: this.name
    val toolParameters = this.parameters.mapNotNull { param ->
        val parameterName = param.name ?: return@mapNotNull null // likely `this` parameter
        val toolParameterDescription =
            param.getPreferredParameterDescriptionAnnotation(this)?.description ?: parameterName
        val paramType = param.type
        val paramToolType = paramType.asToolType()
        val isOptional = param.isOptional
        val parameterDescriptor = ToolParameterDescriptor(
            name = parameterName, type = paramToolType, description = toolParameterDescription
        )
        ParamInfo(descriptor = parameterDescriptor, isOptional = isOptional)
    }

    return ToolDescriptor(
        name = toolName,
        description = toolDescription,
        requiredParameters = toolParameters.filter { !it.isOptional }.map { it.descriptor },
        optionalParameters = toolParameters.filter { it.isOptional }.map { it.descriptor })
}

/**
 * Represents information about a parameter for a tool.
 *
 * This class is used to encapsulate metadata about a tool parameter, including its descriptor
 * and whether the parameter is optional. It provides a structured way to handle parameters
 * when reflecting on callable tools and their associated metadata.
 *
 * @property descriptor The descriptor containing detailed information about the parameter,
 * such as its name, type, and description.
 * @property isOptional Indicates whether this parameter is optional.
 */
private class ParamInfo(
    val descriptor: ToolParameterDescriptor,
    val isOptional: Boolean
)

/**
 * Retrieves the preferred `Tool` annotation for the current function by checking for annotations
 * directly on the function or inherited from implemented methods, if applicable.
 *
 * @return The `Tool` annotation if present, otherwise null.
 */
private fun KFunction<*>.getPreferredToolAnnotation(): Tool? {
    return getToolMethodAndAnnotation()?.second
}

/**
 * Retrieves the preferred `LLMDescription` annotation for the current function, if available.
 * The preferred annotation is determined by checking if the annotation exists directly on
 * the function itself or by evaluating other related methods through `getPreferredToolDescriptionAndMethod`.
 *
 * @return The `LLMDescription` annotation associated with the function, or `null` if no such annotation is found.
 */
private fun KFunction<*>.getPreferredToolDescriptionAnnotation(): LLMDescription? {
    return getPreferredToolDescriptionAndMethod()?.second
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
private fun KParameter.getPreferredParameterDescriptionAnnotation(method: KFunction<*>): LLMDescription? {
    val thisParameterDescription = findAnnotation<LLMDescription>()
    if (thisParameterDescription != null) return thisParameterDescription
    val (toolMarkedMethod, _) = method.getToolMethodAndAnnotation() ?: return null
    toolMarkedMethod.parameters.getOrNull(this.index)?.let { m ->
        return m.findAnnotation<LLMDescription>()
    }
    return null
}

/**
 * Retrieves the first available `Tool` annotation associated with a function.
 * The method first checks if the current function is annotated with the `Tool` annotation.
 * If the current function does not have the annotation, it traverses implemented functions in the inheritance hierarchy
 * to find a function with a `Tool` annotation.
 *
 * @return A `Pair` where the first component is the `KFunction` (either the current or an inherited one) and the second component
 *         is the `Tool` annotation found on that function. Returns `null` if no `Tool` annotation is found.
 */
private fun KFunction<*>.getToolMethodAndAnnotation(): Pair<KFunction<*>, Tool>? {
    // Annotation exactly on this function is preferred
    val thisAnnotation = findAnnotation<Tool>()
    if (thisAnnotation != null) return this to thisAnnotation
    return getImplementedMethods().mapNotNull { m -> m.findAnnotation<Tool>()?.let { m to it } }.firstOrNull()
}

/**
 * Finds and returns the preferred `LLMDescription` annotation along with the corresponding `KFunction`.
 * The function prioritizes the annotation defined directly on the current function.
 * If no annotation is found on the current function, it searches the implemented methods for a tool method annotated with `LLMDescription`.
 *
 * @return A `Pair` containing the `KFunction` and `LLMDescription` annotation if found,
 * or `null` if no suitable annotation is available either on the function itself or its implemented methods.
 */
private fun KFunction<*>.getPreferredToolDescriptionAndMethod(): Pair<KFunction<*>, LLMDescription>? {
    // Annotation exactly on this function is preferred
    val thisAnnotation = findAnnotation<LLMDescription>()
    if (thisAnnotation != null) return this to thisAnnotation

    val (toolMethod, _) = getToolMethodAndAnnotation() ?: return null
    val lLMDescriptionAnnotation = toolMethod.findAnnotation<LLMDescription>() ?: return null
    return toolMethod to lLMDescriptionAnnotation
}

/**
 * Retrieves a sequence of methods that are implemented by the current function within its class hierarchy.
 *
 * The sequence includes all methods that are overridden by the current function, traversing through
 * the class and interface hierarchy of the declaring class of this function. It skips any methods
 * that are identical to the current function.
 *
 * @return A sequence of Kotlin functions implemented by the current function, traversing its class and interface hierarchy.
 */
private fun KFunction<*>.getImplementedMethods(): Sequence<KFunction<*>> {
    return sequence {
        val javaMethod = this@getImplementedMethods.javaMethod ?: return@sequence
        val methodName = javaMethod.name
        val parameterTypes = javaMethod.parameterTypes
        val visited = mutableSetOf<Class<*>>()
        val queue = ArrayDeque<Class<*>>()

        queue.add(javaMethod.declaringClass)

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
                val kotlinMethod =
                    currentClass.getDeclaredMethod(methodName, *parameterTypes).kotlinFunction ?: continue
                if (kotlinMethod != this@getImplementedMethods) yield(kotlinMethod)
            } catch (_: NoSuchMethodException) {
                // Method not found in this class/interface, continue traversal
            }
        }
    }
}
