package ai.koog.agents.core.tools.annotations

/**
 * Marks elements of the agent tools API that are internal and primarily intended for use within
 * the implementation of tools and agents. APIs marked with this annotation are not considered stable
 * and may change or be removed without warning in any release.
 *
 * Opting into these APIs indicates an understanding that they may undergo breaking changes, and should
 * be used with caution in any external implementations or client code.
 */
@RequiresOptIn
public annotation class InternalAgentToolsApi
