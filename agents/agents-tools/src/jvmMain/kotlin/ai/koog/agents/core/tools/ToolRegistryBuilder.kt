@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.tools

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.core.tools.reflect.java.asTool
import java.lang.reflect.Method

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class ToolRegistryBuilder {
    private val builder = ToolRegistry.Builder()

    public actual fun tool(tool: Tool<*, *>): ToolRegistryBuilder = apply { builder.tool(tool) }

    public actual fun tools(toolsList: List<Tool<*, *>>): ToolRegistryBuilder = apply { builder.tools(toolsList) }

    public actual fun build(): ToolRegistry = builder.build()

    /**
     * Registers [toolFunction] as a tool in the [ToolRegistry].
     *
     * @see asTool
     */
    @OptIn(InternalAgentToolsApi::class)
    @JvmOverloads
    @JavaAPI
    public fun ToolRegistry.Builder.tool(
        toolFunction: Method,
        thisRef: Any? = null,
        name: String? = null,
        description: String? = null
    ): ToolRegistry.Builder = apply {
        tool(toolFunction.asTool(thisRef, name, description))
    }

    /**
     * Registers a set of tools from the [instance] in the [ToolRegistry] .
     */
    @JavaAPI
    public fun tools(
        instance: Any,
    ): ToolRegistryBuilder = apply {
        tools(instance::class.asTools(instance))
    }
}
