package ai.koog.agents.core.annotation

/**
 * Marks an API as experimental for agent-related implementations.
 *
 * APIs annotated with `ExperimentalAgentsApi` are not considered stable and may change, be removed,
 * or have their behavior modified in future updates. This warning is provided to
 * indicate that the use of such APIs requires caution and acknowledgment of potential instability.
 *
 * Note that no guarantees are made regarding backward compatibility of elements marked with this annotation.
 */
@RequiresOptIn("This API is experimental and is likely to change in the future.")
public annotation class ExperimentalAgentsApi
