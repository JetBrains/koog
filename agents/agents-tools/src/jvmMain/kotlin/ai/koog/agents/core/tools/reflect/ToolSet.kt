package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlin.reflect.jvm.jvmName

/**
 * A marker interface for a set of tools that can be converted to a list of tools via reflection.
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
        get() = this.javaClass.getAnnotationsByType(LLMDescription::class.java).firstOrNull()?.value
            ?: this::class.jvmName

    /**
     * Converts all instance methods marked as [Tool] to a list of tools.
     *
     * See [asTool] for detailed description.
     */
    public fun asTools(): List<ToolFromCallable<*>> {
        return this::class.asTools(thisRef = this)
    }

    /**
     * Retrieves a tool by its name from the toolset. If the tool is not found, an exception is thrown.
     *
     * @param name The name of the tool to retrieve.
     * @throws IllegalStateException If no tool with the specified name is found.
     */
    public fun getTool(name: String): ToolFromCallable<*> {
        return asTools().find { it.name == name } ?: error("Tool $name not found")
    }
}
