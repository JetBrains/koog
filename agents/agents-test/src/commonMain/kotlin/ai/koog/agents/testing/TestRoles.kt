package ai.koog.agents.testing

import ai.koog.agents.core.tools.permissions.*

/**
 * Shared test roles for use across test suites to reduce duplication.
 * These provide common role hierarchy scenarios for testing permissions.
 */
public object TestRoles {
    
    /**
     * Modern type-safe roles using the new Roles base class.
     * This is the recommended approach for defining role hierarchies.
     */
    public class Standard : Roles() {
        public val guest: Role by role {
            name = "guest"
            description = "Guest role with read-only access"
        }
        
        public val user: Role by role {
            name = "user"
            description = "Standard user role"
            extends = guest
        }
        
        public val admin: Role by role {
            name = "admin"
            description = "Administrator role with full access"
            extends = user
            isAdmin = true
        }
        
        public val moderator: Role by role {
            name = "moderator"
            description = "Content moderator role"
            extends = user
        }
        
        public val premium: Role by role {
            name = "premium"
            description = "Premium user with enhanced limits"
            extends = user
        }
    }
    
    /**
     * Create standard test roles using the new pattern.
     */
    public fun standard(): Standard = Standard()
    
    // ========================================
    // Legacy patterns - kept for backward compatibility
    // ========================================
    
    /**
     * Guest role - basic level with minimal permissions
     */
    @Deprecated("Use TestRoles.Standard instead", ReplaceWith("TestRoles.Standard().guest"))
    public val guest: Role = Role(
        name = "guest",
        description = "Guest role with read-only access",
        inherits = emptySet()
    )
    
    /**
     * User role - standard user that inherits from guest
     */
    @Deprecated("Use TestRoles.Standard instead", ReplaceWith("TestRoles.Standard().user"))
    public val user: Role = Role(
        name = "user", 
        description = "Standard user role",
        inherits = setOf(guest)
    )
    
    /**
     * Admin role - administrative privileges, inherits from user
     */
    @Deprecated("Use TestRoles.Standard instead", ReplaceWith("TestRoles.Standard().admin"))
    public val admin: Role = Role(
        name = "admin",
        description = "Administrator role with full access", 
        inherits = setOf(user),
        builtinType = BuiltinRoleType.Admin()
    )
    
    /**
     * Moderator role - content moderation, inherits from user
     */
    @Deprecated("Use TestRoles.Standard instead", ReplaceWith("TestRoles.Standard().moderator"))
    public val moderator: Role = Role(
        name = "moderator",
        description = "Content moderator role",
        inherits = setOf(user)
    )
    
    /**
     * Premium role - enhanced user privileges, inherits from user
     */
    @Deprecated("Use TestRoles.Standard instead", ReplaceWith("TestRoles.Standard().premium"))
    public val premium: Role = Role(
        name = "premium",
        description = "Premium user with enhanced limits",
        inherits = setOf(user)
    )
    
    /**
     * Create a type-safe role set for testing with DSL
     */
    @Deprecated("Use TestRoles.Standard instead", ReplaceWith("TestRoles.Standard()"))
    public class TypeSafeTestRoles(builder: RoleHierarchyBuilder) : RoleSet(builder) {
        public val guest: Role = role {
            name = "guest"
            description = "Guest role"
        }
        
        public val user: Role = role {
            name = "user"
            description = "User role"
            extends = guest
        }
        
        public val admin: Role = role {
            name = "admin"
            description = "Admin role"
            extends = user
            isAdmin = true
        }
        
        public val moderator: Role = role {
            name = "moderator"
            description = "Moderator role"
            extends = user
        }
        
        public val premium: Role = role {
            name = "premium"
            description = "Premium role"
            extends = user
        }
    }
    
    /**
     * Create a complete role hierarchy for testing
     */
    @Deprecated("Use TestRoles.Standard instead", ReplaceWith("TestRoles.Standard().hierarchy"))
    public fun createRoleHierarchy(): RoleHierarchy = RoleHierarchy(
        mapOf(
            "guest" to guest,
            "user" to user,
            "admin" to admin,
            "moderator" to moderator,
            "premium" to premium
        )
    )
    
    /**
     * Create type-safe roles for testing
     */
    @Deprecated("Use TestRoles.Standard instead", ReplaceWith("TestRoles.Standard()"))
    public fun createTypeSafeRoles(): TypeSafeTestRoles = RoleSet.create { TypeSafeTestRoles(it) }
}