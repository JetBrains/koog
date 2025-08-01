package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlin.reflect.jvm.jvmName

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
}
