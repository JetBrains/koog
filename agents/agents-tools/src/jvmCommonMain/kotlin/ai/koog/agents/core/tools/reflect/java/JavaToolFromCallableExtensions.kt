package ai.koog.agents.core.tools.reflect.java

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.core.tools.reflect.asTools
import java.lang.reflect.Method
import kotlin.reflect.jvm.kotlinFunction

/**
 * Converts a Java [Method] into a [ToolFromCallable].
 *
 * @param thisRef The object instance to use as the 'this' object for the callable.
 * @param name The name of the tool. If not provided, the name will be obtained from [Tool.customName] property.
 * In the case of the missing attribute or empty name the name of the function is used.
 * @param description The description of the tool. If not provided, the description will be obtained from [LLMDescription.description] property.
 * In the case of the missing attribute or empty description the name of the function is used as a description.
 */
@InternalAgentToolsApi
@JavaAPI
public fun Method.asTool(
    thisRef: Any? = null,
    name: String? = null,
    description: String? = null
): ToolFromCallable<*> {
    val kFunction = kotlinFunction ?: throw IllegalArgumentException("Can't convert $this to KFunction")
    return kFunction.asTool(thisRef, name, description)
}

/**
 * Converts all functions of [this] class marked with [Tool] annotation to a list of tools.
 *
 * @param thisRef an instance of [this] class to be used as the 'this' object for the callable in the case of instance methods.

 * @see asTool
 */
@OptIn(InternalAgentToolsApi::class)
@JavaAPI
public fun <T : Any> Class<out T>.asTools(
    thisRef: T? = null
): List<ToolFromCallable<*>> {
    return kotlin.asTools(thisRef = thisRef)
}
