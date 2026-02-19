@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.tools

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.java.asJavaTools
import ai.koog.agents.core.tools.reflect.java.asTool
import ai.koog.agents.core.tools.reflect.tool
import kotlinx.serialization.json.Json
import kotlin.reflect.KFunction

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class ToolRegistryBuilder {
    private val builder = ToolRegistry.Builder()

    public actual fun tool(tool: Tool<*, *>): ToolRegistryBuilder = apply { builder.tool(tool) }

    public actual fun tools(toolsList: List<Tool<*, *>>): ToolRegistryBuilder = apply { builder.tools(toolsList) }

    public actual fun build(): ToolRegistry = builder.build()

    /**
     * Registers a tool in the `ToolRegistryBuilder` using a specified function and optional parameters
     * to configure its metadata and behavior.
     *
     * @param toolFunction The Kotlin function to be registered as a tool. Defines the functionality of the tool.
     * @param json The `Json` serialization instance used to serialize and deserialize input and output for the tool. Defaults to a standard `Json` instance.
     * @param thisRef Optional reference to the object instance where the tool function is defined. Useful for instance-bound functions. Defaults to `null`.
     * @param name An optional string to explicitly name the tool. If `null`, a name will be automatically derived from the function.
     * @param description An optional string providing a description for the tool. Useful for documentation or explanatory purposes. Defaults to `null`.
     * @return The `ToolRegistryBuilder` instance to allow method chaining.
     */
    @JvmOverloads
    @JavaAPI
    public fun tool(
        toolFunction: KFunction<*>,
        json: Json = Json,
        thisRef: Any? = null,
        name: String? = null,
        description: String? = null
    ): ToolRegistryBuilder = apply {
        builder.tool(toolFunction, json, thisRef, name, description)
    }

    /**
     * Registers a Java `Method` as a tool in the `ToolRegistryBuilder`.
     *
     * This method adds a tool to the registry builder by converting the provided Java reflection `Method` into a
     * `Tool` object. The tool metadata and behavior can be customized using optional parameters for serialization,
     * instance references, name, and description.
     *
     * @param method The Java `Method` instance to be registered as a tool.
     * @return The current instance of `ToolRegistryBuilder` to allow method chaining.
     */
    @OptIn(InternalAgentToolsApi::class)
    @JvmOverloads
    @JavaAPI
    public fun tool(method: java.lang.reflect.Method): ToolRegistryBuilder = apply {
        tool(method.asTool())
    }

    /**
     * Registers all tools defined in the given [ToolSet] to the tool registry builder.
     * The tools are converted into a list of Java-compatible [Tool]s and added to the registry.
     *
     * @param toolSet The set of tools to be added to the registry. Must implement [ToolSet].
     * @param json The `Json` instance to use for serialization and deserialization of tools. Defaults to a standard [Json] instance.
     */
    @JvmOverloads
    @JavaAPI
    public fun tools(
        toolSet: ToolSet,
        json: Json = Json
    ): ToolRegistryBuilder = apply {
        tools(toolSet.asJavaTools(json = json))
    }
}
