package ai.koog.agents.core.tools.permissions

/**
 * Exception thrown when a permission check fails.
 */
public class PermissionDeniedException(
    message: String,
    cause: Throwable? = null,
    public val nodeName: String? = null,
    public val requiredRoles: Set<Role> = emptySet(),
    public val minimumRole: Role? = null,
    public val currentRoles: Set<Role>? = null
) : Exception(message, cause)
