@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.core.tools.reflect.java.asJavaTools
import kotlin.reflect.KFunction

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")
public actual open class RollbackToolRegistryBuilder actual constructor(
    internal actual val delegate: RollbackToolRegistryBuilderImpl
) : RollbackToolRegistryBuilderAPI {

    /**
     * Registers relationships between tools in the given tool set and their corresponding rollback tools
     * in the rollback tool set.
     *
     * @param toolSet The set of tools to register, each of which represents a primary tool.
     * @param rollbackToolSet The set of rollback tools that correspond to the tools in the provided tool set.
     */
    @JavaAPI
    public fun registerRollbacks(toolSet: ToolSet, rollbackToolSet: RollbackToolSet): RollbackToolRegistryBuilder =
        apply {
            val normalTools = toolSet.asJavaTools()
            val revertTools = normalTools.mapNotNull { tool ->
                rollbackToolSet.revertToolFor(tool.name, toolSet)
            }

            if (!normalTools.containsAll(revertTools)) {
                throw IllegalArgumentException(
                    "The provided rollback tool set does not contain a rollback tool for each tool in the provided tool set. " +
                        "Missing tools: ${revertTools.joinToString(", ") { it.name }}"
                )
            }

            if (!revertTools.containsAll(normalTools)) {
                throw IllegalArgumentException(
                    "The provided tool set does not contain a rollback tool for each tool in the provided rollback tool set. " +
                        "Missing tools: ${normalTools.joinToString(", ") { it.name }}"
                )
            }

            normalTools.zip(revertTools)
                .forEach { (tool, revertTool) -> delegate.registerRollbackUnsafe(tool, revertTool) }
        }

    actual override fun <TArgs> registerRollback(
        tool: Tool<TArgs, *>,
        rollbackTool: Tool<TArgs, *>
    ): RollbackToolRegistryBuilder = apply { delegate.registerRollback(tool, rollbackTool) }

    /**
     * Registers a relationship between a tool and its corresponding rollback tool using the specified functions.
     *
     * @param toolFunction The function representing the primary tool to register.
     * @param rollbackToolFunction The function representing the rollback counterpart to the primary tool.
     */
    public fun registerRollback(
        toolFunction: KFunction<*>,
        rollbackToolFunction: KFunction<*>
    ) {
        this.registerRollback(toolFunction.asTool(), rollbackToolFunction.asTool())
    }

    actual override fun build(): RollbackToolRegistry = delegate.build()
}
