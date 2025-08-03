package ai.koog.agents.core.tools.permissions

/**
 * Represents a role in the permission system.
 *
 * Roles can inherit from other roles, creating a hierarchy. A role
 * automatically has all permissions of the roles it inherits from.
 *
 * @property name The unique name of this role
 * @property description Human-readable description of this role
 * @property inherits Direct parent roles this role inherits from
 * @property builtinType The type of built-in role (Regular or Admin)
 */
public data class Role(
    public val name: String,
    public val description: String? = null,
    public val inherits: Set<Role> = emptySet(),
    public val builtinType: BuiltinRoleType = BuiltinRoleType.Regular
) {
    /**
     * Check if this role has admin privileges.
     * Uses cycle detection to prevent infinite loops in circular hierarchies.
     */
    public fun hasAdminPrivileges(): Boolean {
        val visited = mutableSetOf<Role>()
        return hasAdminPrivilegesRecursive(visited)
    }

    private fun hasAdminPrivilegesRecursive(visited: MutableSet<Role>): Boolean {
        if (builtinType is BuiltinRoleType.Admin) return true
        if (!visited.add(this)) return false // Cycle detection
        return inherits.any { it.hasAdminPrivilegesRecursive(visited) }
    }

    /**
     * Check if this role is or inherits from the given role.
     * Uses cycle detection to prevent infinite loops in circular hierarchies.
     *
     * @param role The role to check for
     * @return true if this role is the given role or inherits from it
     * @throws IllegalStateException if a circular role hierarchy is detected
     */
    public fun hasRole(role: Role): Boolean {
        val visited = mutableSetOf<Role>()
        return hasRoleRecursive(role, visited)
    }

    private fun hasRoleRecursive(role: Role, visited: MutableSet<Role>): Boolean {
        if (this == role) return true
        if (!visited.add(this)) {
            throw IllegalStateException("Circular role hierarchy detected involving role: $name")
        }
        return inherits.any { it.hasRoleRecursive(role, visited) }
    }

    /**
     * Get all roles this role inherits from (transitively).
     *
     * @return Set of all inherited roles
     */
    public fun getAllInheritedRoles(): Set<Role> {
        val result = mutableSetOf<Role>()
        collectInheritedRoles(result)
        return result
    }

    private fun collectInheritedRoles(result: MutableSet<Role>) {
        for (parent in inherits) {
            if (result.add(parent)) {
                parent.collectInheritedRoles(result)
            }
        }
    }

    override fun toString(): String =
        "Role($name${if (hasAdminPrivileges()) " [Admin]" else ""})"
}

/**
 * Represents the type of built-in role.
 */
public sealed class BuiltinRoleType {
    /**
     * Regular user-defined role with no special privileges.
     */
    public object Regular : BuiltinRoleType()

    /**
     * Admin role with elevated privileges.
     */
    public data class Admin(
        public val superAdmin: Boolean = false
    ) : BuiltinRoleType()
}
