package ai.grazie.code.agents.core.tools.reflect

import ai.grazie.code.agents.core.tools.annotations.LLMDescription
import kotlin.reflect.jvm.jvmName

/**
 * A marker interface for a set of tools that can be converted to a list of [ai.grazie.code.agents.core.tools.Tool]s via reflection using [asTools].
 *
 * @see ToolSet.asTools
 *
 */
interface ToolSet {
    val name: String
        get() = this.javaClass.getAnnotationsByType(LLMDescription::class.java).firstOrNull()?.description ?: this::class.jvmName
}