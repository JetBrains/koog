package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * Converts [KFunction] to [ToolFromCallable].
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
 * @param thisRef The object instance to use as the 'this' object for the callable.
 * @param name The name of the tool. If not provided, the name will be obtained from [Tool.customName] property.
 * In the case of the missing attribute or empty name the name of the function is used.
 * @param description The description of the tool. If not provided, the description will be obtained from [LLMDescription.description] property.
 * In the case of the missing attribute or empty description the name of the function is used as a description.
 */
@OptIn(InternalAgentToolsApi::class)
public fun <R> KFunction<R>.asTool(
    thisRef: Any? = null,
    name: String? = null,
    description: String? = null
): ToolFromCallable<R> {
    return ToolFromCallable(
        callable = this,
        thisRef = thisRef,
        name = name,
        description = description
    )
}

/**
 * Converts all functions of [this] class marked with [Tool] annotation to a list of tools.
 *
 * @param thisRef an instance of [this] class to be used as the 'this' object for the callable in the case of instance methods.

 * @see asTool
 */
@OptIn(InternalAgentToolsApi::class)
public fun <T : Any> KClass<out T>.asTools(
    thisRef: T? = null
): List<ToolFromCallable<*>> {
    return this.functions.filter { m ->
        m.getPreferredToolAnnotation() != null
    }.map {
        it.asTool(thisRef = thisRef)
    }.apply {
        require(isNotEmpty()) { "No tools found in ${this@asTools}" }
    }
}

/**
 * Converts all instance methods of class/interface [T] marked as [Tool] to a list of tools that will be called on object [this].
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
@OptIn(InternalAgentToolsApi::class)
public inline fun <reified T : Any> T.asToolsByClass(): List<ToolFromCallable<*>> {
    return T::class.asTools(thisRef = this)
}

/**
 * Registers [toolFunction] as a tool in the [ToolRegistry].
 *
 * @see asTool
 */
@OptIn(InternalAgentToolsApi::class)
public fun ToolRegistry.Builder.tool(
    toolFunction: KFunction<*>,
    thisRef: Any? = null,
    name: String? = null,
    description: String? = null
): ToolRegistry.Builder = apply {
    tool(toolFunction.asTool(thisRef, name, description))
}

/**
 * Registers a set of tools from the [instance] in the [ToolRegistry] .
 */
@OptIn(InternalAgentToolsApi::class)
public fun ToolRegistry.Builder.tools(
    instance: Any,
) {
    tools(instance::class.asTools(instance))
}

/**
 * Retrieves the preferred [Tool] annotation for the current function by checking for annotations
 * directly on the function or inherited from implemented methods, if applicable.
 *
 * @return The [Tool] annotation if present, otherwise null.
 */
private fun KFunction<*>.getPreferredToolAnnotation(): Tool? {
    // Annotation exactly on this function is preferred
    val thisAnnotation = findAnnotation<Tool>()
    if (thisAnnotation != null) return thisAnnotation

    @Suppress("SimplifiableCallChain")
    return getImplementedMethods()
        .mapNotNull { m -> m.findAnnotation<Tool>() }
        .firstOrNull()
}

/**
 * Retrieves a list of methods that are implemented by the current function within its class hierarchy.
 */
private fun KFunction<*>.getImplementedMethods(): List<KFunction<*>> {
    val javaMethod = this.javaMethod ?: return emptyList()
    val methodName = javaMethod.name
    val parameterTypes = javaMethod.parameterTypes
    return javaMethod.declaringClass.kotlin.allSuperclasses
        .toList()
        .mapNotNull { superclass ->
            try {
                superclass.java
                    .getDeclaredMethod(methodName, *parameterTypes)
                    .kotlinFunction
                    ?.takeIf { it != this }
            } catch (_: NoSuchMethodException) {
                null
            }
        }
}
