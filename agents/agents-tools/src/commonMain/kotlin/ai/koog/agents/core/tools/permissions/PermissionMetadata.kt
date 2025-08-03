package ai.koog.agents.core.tools.permissions

/**
 * Permission configuration for a graph component (strategy, subgraph, or node).
 *
 * This metadata can be attached to any graph element to define access control
 * requirements. The permission system will check these requirements before
 * allowing execution of the associated component.
 */
public data class PermissionMetadata(
    /**
     * Set of roles where at least one is required to access this component.
     * If empty, no specific role requirements beyond minimumRole.
     */
    public val requiredRoles: Set<Role> = emptySet(),

    /**
     * The minimum role level required to access this component.
     * If null, defaults to the system's default role.
     */
    public val minimumRole: Role? = null
)

/**
 * DSL builder for creating PermissionMetadata instances.
 */
public class PermissionBuilder {
    private val _requiredRoles: MutableSet<Role> = mutableSetOf()

    /**
     * The minimum role level required to access this component.
     */
    public var minimumRole: Role? = null

    /**
     * Add a required role to the set of required roles.
     */
    public fun requireRole(role: Role) {
        _requiredRoles.add(role)
    }

    /**
     * Add multiple required roles.
     */
    public fun requireRoles(vararg roles: Role) {
        _requiredRoles.addAll(roles)
    }

    /**
     * Build the PermissionMetadata instance.
     */
    public fun build(): PermissionMetadata = PermissionMetadata(
        requiredRoles = _requiredRoles.toSet(),
        minimumRole = minimumRole
    )
}

/**
 * DSL function to create PermissionMetadata using a builder.
 */
public inline fun permissions(block: PermissionBuilder.() -> Unit): PermissionMetadata {
    return PermissionBuilder().apply(block).build()
}
