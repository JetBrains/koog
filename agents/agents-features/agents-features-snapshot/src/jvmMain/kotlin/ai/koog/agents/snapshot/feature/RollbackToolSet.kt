@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.feature

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.java.ToolFromJavaMethod
import ai.koog.agents.core.tools.reflect.java.asTool

/**
 * Represents a set of tools that can perform rollback or reversal operations for a given tool and toolset.
 * This interface provides a mechanism to locate and retrieve tools that revert specific operations.
 */
@Suppress("UNCHECKED_CAST")
@JavaAPI
public interface RollbackToolSet {
    /**
     * Attempts to locate and retrieve a tool that reverts the behavior of another tool, given its name
     * and associated toolset. The method searches for Java methods in the current class that are annotated
     * with the `@Reverts` annotation, matching both the specified tool name and toolset type.
     *
     * @param toolName The name of the tool to be reverted.
     * @param toolSet The toolset associated with the tool to be reverted.
     * @return A tool that reverts the specified tool if found, or `null` if no matching tool is located.
     */
    @OptIn(InternalAgentToolsApi::class)
    @JavaAPI
    public fun revertToolFor(toolName: String, toolSet: ToolSet): Tool<ToolFromJavaMethod.VarArgs, *>? {
        return this::class.java.methods
            .find {
                it.isAnnotationPresent(Reverts::class.java) &&
                    it.getAnnotation(Reverts::class.java).toolName == toolName &&
                    it.getAnnotation(Reverts::class.java).toolSet.isInstance(toolSet)
            }
            ?.asTool() as? Tool<ToolFromJavaMethod.VarArgs, *>
    }
}
