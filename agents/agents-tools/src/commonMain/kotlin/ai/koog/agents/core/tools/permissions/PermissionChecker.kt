package ai.koog.agents.core.tools.permissions

/**
 * Interface for checking if an agent has permission to use a tool.
 */
public interface PermissionChecker {
    /**
     * Check if the given roles satisfy the permission requirements.
     *
     * @param agentRoles Roles assigned to the agent
     * @param permission Permission requirements for the tool
     * @return Permission check result
     */
    public fun checkPermission(
        agentRoles: Set<Role>,
        permission: ToolPolicy
    ): PermissionCheckResult

    /**
     * Check permission for a single role.
     * Default implementation delegates to the set-based method.
     */
    public fun checkPermission(
        role: Role,
        permission: ToolPolicy
    ): PermissionCheckResult = checkPermission(setOf(role), permission)
}

/**
 * Standard implementation of permission checking based on role hierarchy.
 */
public class StandardPermissionChecker : PermissionChecker {
    override fun checkPermission(
        agentRoles: Set<Role>,
        permission: ToolPolicy
    ): PermissionCheckResult {
        require(agentRoles.isNotEmpty()) { "Agent must have at least one role" }
        
        return when (val requirement = permission.roleRequirement) {
            is RoleRequirement.None -> PermissionCheckResult.Granted

            is RoleRequirement.MinimumRole -> {
                try {
                    val hasMinimumRole = agentRoles.any { it.hasRole(requirement.role) }
                    if (hasMinimumRole) {
                        PermissionCheckResult.Granted
                    } else {
                        PermissionCheckResult.Denied(
                            "Requires minimum role: ${requirement.role.name}, " +
                            "but agent has: ${agentRoles.joinToString { it.name }}"
                        )
                    }
                } catch (e: IllegalStateException) {
                    PermissionCheckResult.Denied(
                        "Permission check failed due to invalid role hierarchy: ${e.message}"
                    )
                }
            }

            is RoleRequirement.AllowedRoles -> {
                require(requirement.roles.isNotEmpty()) { "AllowedRoles requirement must specify at least one role" }
                
                try {
                    val hasAllowedRole = requirement.roles.any { allowed ->
                        agentRoles.any { it == allowed } // Direct equality, no hierarchy
                    }
                    if (hasAllowedRole) {
                        PermissionCheckResult.Granted
                    } else {
                        val roleNames = requirement.roles.joinToString { it.name }
                        val agentRoleNames = agentRoles.joinToString { it.name }
                        PermissionCheckResult.Denied(
                            "Requires one of these specific roles: [$roleNames], " +
                            "but agent has: [$agentRoleNames]"
                        )
                    }
                } catch (e: IllegalStateException) {
                    PermissionCheckResult.Denied(
                        "Permission check failed due to invalid role hierarchy: ${e.message}"
                    )
                }
            }
        }
    }

    public companion object {
        /**
         * Default instance of the standard permission checker.
         */
        public val Default: StandardPermissionChecker = StandardPermissionChecker()
    }
}

/**
 * Result of a permission check.
 */
public sealed class PermissionCheckResult {
    /**
     * Permission granted.
     */
    public object Granted : PermissionCheckResult()

    /**
     * Permission denied.
     *
     * @property reason Why permission was denied
     */
    public data class Denied(
        public val reason: String
    ) : PermissionCheckResult()
}
