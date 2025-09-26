package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.tools.reflect.asTool
import kotlin.reflect.KFunction

/**
 * Registers a relationship between a tool and its corresponding rollback tool using the specified functions.
 *
 * @param toolFunction The function representing the primary tool to register.
 * @param rollbackToolFunction The function representing the rollback counterpart to the primary tool.
 */
public fun RollbackToolRegistry.Builder.registerRollback(
    toolFunction: KFunction<*>,
    rollbackToolFunction: KFunction<*>
) {
    this.registerRollback(toolFunction.asTool(), rollbackToolFunction.asTool())
}

/**
 * Adds a tool and its corresponding rollback tool to the registry.
 * This convenience method converts both `toolFunction` and `rollbackToolFunction` into `Tool` objects before adding them.
 *
 * @param toolFunction The Kotlin function representing the primary tool to add.
 * @param rollbackToolFunction The Kotlin function representing the rollback tool associated with the primary tool.
 */
public fun RollbackToolRegistry.add(
    toolFunction: KFunction<*>,
    rollbackToolFunction: KFunction<*>
) {
    this.add(toolFunction.asTool(), rollbackToolFunction.asTool())
}
