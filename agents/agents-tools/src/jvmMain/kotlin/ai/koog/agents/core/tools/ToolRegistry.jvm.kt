package ai.koog.agents.core.tools

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.core.tools.reflect.java.asJavaTools
import ai.koog.agents.core.tools.reflect.java.asTool
import ai.koog.agents.core.tools.reflect.tool
import kotlinx.serialization.json.Json
import kotlin.reflect.KFunction

/**
 * A builder class for constructing a `ToolRegistry`.
 *
 * The `ToolRegistryBuilder` provides an API for adding tools to the registry, either individually or as a list,
 * and for finalizing the construction of the registry. It ensures each tool has a unique name and that
 * the registry is configured appropriately.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@JavaAPI
public actual class ToolRegistryBuilder {
    /**
     * Instance of `ToolRegistry.Builder` used to construct and manage the registration of tools.
     *
     * The `builder` acts as the internal mechanism for incrementally adding tools to a `ToolRegistry`.
     * It ensures that each registered tool has a unique name and provides methods to finalize the registry construction process.
     *
     * The builder supports:
     * - Adding single or multiple tools to the registry.
     * - Finalizing the registration and creating a `ToolRegistry` instance containing the registered tools.
     */
    @JavaAPI
    private val builder = ToolRegistry.Builder()

    /**
     * Adds a tool to the registry builder. This method registers a given tool, allowing it to be included
     * in the resulting tool registry when built.
     *
     * @param tool The tool instance to be added. Must not conflict with an already registered tool of the same name.
     * @return The instance of the `ToolRegistryBuilder` for method chaining.
     */
    @JavaAPI
    public actual fun tool(tool: Tool<*, *>): ToolRegistryBuilder = apply { builder.tool(tool) }

    /**
     * Adds a list of tools to the tool registry builder.
     *
     * @param toolsList The list of tools to be added to the registry. Each tool is represented by an instance of [Tool] with specific arguments and result types.
     * @return The current instance of [ToolRegistryBuilder] to enable method chaining.
     */
    @JavaAPI
    public actual fun tools(toolsList: List<Tool<*, *>>): ToolRegistryBuilder = apply { builder.tools(toolsList) }

    /**
     * Finalizes the tool registry configuration and constructs a `ToolRegistry` instance.
     *
     * This method gathers all tools added to the builder and creates a new `ToolRegistry`
     * instance containing those tools.
     *
     * @return A `ToolRegistry` instance containing the tools registered through the builder.
     */
    @JavaAPI
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
