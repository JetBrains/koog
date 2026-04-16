@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.tools

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.core.tools.reflect.java.asTool
import java.lang.reflect.Method
import kotlin.reflect.KFunction

public actual class ToolRegistryBuilder {
    private val tools = mutableListOf<Tool<*, *>>()

    /**
     * Add a tool to the registry
     */
    public actual fun tool(tool: Tool<*, *>): ToolRegistryBuilder = apply {
        tools.addTool(tool)
    }

    /**
     * Add multiple tools to the registry
     */
    public actual fun tools(toolsList: List<Tool<*, *>>): ToolRegistryBuilder = apply {
        toolsList.forEach { tool(it) }
    }

    public actual fun build(): ToolRegistry {
        return ToolRegistry(tools.toList())
    }

    /**
     * Registers a set of tools from the [instance] in the [ToolRegistry] .
     */
    @OptIn(InternalAgentToolsApi::class)
    public fun tools(
        instance: ToolSet,
    ): ToolRegistryBuilder = apply {
        tools(instance::class.asTools(instance))
    }

    /**
     * Registers [toolFunction] as a tool in the [ToolRegistry].
     *
     * @see asTool
     */
    @OptIn(InternalAgentToolsApi::class)
    public fun tool(
        toolFunction: KFunction<*>,
        thisRef: Any? = null,
        name: String? = null,
        description: String? = null
    ): ToolRegistryBuilder = apply {
        tool(toolFunction.asTool(thisRef, name, description))
    }

    /**
     * Registers [toolFunction] as a tool in the [ToolRegistry].
     *
     * @see asTool
     */
    @OptIn(InternalAgentToolsApi::class)
    @JvmOverloads
    @JavaAPI
    public fun tool(
        toolFunction: Method,
        thisRef: Any? = null,
        name: String? = null,
        description: String? = null
    ): ToolRegistryBuilder = apply {
        tool(toolFunction.asTool(thisRef, name, description))
    }
}
