package ai.koog.agents.snapshot.feature;

import ai.koog.agents.core.tools.reflect.ToolSet;

/**
 * Annotation used to mark a method that serves as a rollback or revert tool for a specified primary tool.
 * This annotation provides a mapping between a primary tool name and its corresponding rollback tool.
 *
 * The annotated method can be retrieved dynamically at runtime for performing rollback operations
 * associated with tools defined in a specified {@link ToolSet}.
 */
public @interface Reverts {
    String toolName();

    Class<? extends ToolSet> toolSet();
}
