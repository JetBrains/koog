package ai.koog.agents.core.tools.permissions

/**
 * Standard built-in roles provided by the system.
 */
public object StandardRoles {
    /**
     * Default role that all agents automatically have.
     * This ensures there's always at least one role for permission checks.
     */
    public val defaultRole: Role = Role(
        name = "Default",
        description = "Default role that all agents have",
        builtinType = BuiltinRoleType.Regular
    )
}
