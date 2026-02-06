package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.json.Json
import kotlin.reflect.jvm.jvmName
import ai.koog.agents.core.tools.Tool as ToolType

/**
 * A marker interface for a set of tools that can be converted to a list of [ai.koog.agents.core.tools.Tool]s via reflection using [asTools].
 *
 * @see ToolSet.asTools
 *
 */
public interface ToolSet {
    /**
     * Retrieves the description of the current class or object from the `LLMDescription` annotation.
     * If the annotation is not present, defaults to the JVM name of the class.
     *
     * This property is typically used to provide human-readable descriptions of toolsets
     * or entities for integration with large language models (LLMs).
     */
    public val name: String
        get() = this.javaClass.getAnnotationsByType(LLMDescription::class.java).firstOrNull()?.description
            ?: this::class.jvmName

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
    public fun asTools(json: Json): List<ToolType<ToolFromCallable.VarArgs, *>> {
        return this::class.asTools(json = json, thisRef = this)
    }

    /**
     * Converts all instance methods of [this] class marked as [Tool] to a list of tools.
     *
     * See [asTool] for detailed description.
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
    public fun asTools(): List<ToolType<ToolFromCallable.VarArgs, *>> {
        return asTools(json = Json)
    }

    /**
     * Retrieves a tool by its name from the toolset. If the tool is not found, an exception is thrown.
     *
     * @param name The name of the tool to retrieve.
     * @return The tool of type [ToolType] corresponding to the specified name.
     * @throws IllegalStateException If no tool with the specified name is found.
     */
    public fun getTool(name: String): ToolType<ToolFromCallable.VarArgs, *> {
        return asTools(json = Json).find { it.name == name } ?: error("Tool $name not found")
    }
}
