package ai.koog.agents.core.tools.permissions

/**
 * DSL for defining role hierarchies with implicit ordering.
 *
 * Roles are ordered by declaration - the first role declared is considered
 * the most basic/restricted role, and subsequent roles have increasing privileges.
 *
 * Example:
 * ```kotlin
 * val hierarchy = roleHierarchy {
 *     val guest = role {
 *         name = "guest"
 *         description = "Read-only access"
 *     }
 *
 *     val user = role {
 *         name = "user"
 *         description = "Standard user"
 *         extends = guest  // Single inheritance
 *     }
 *
 *     val admin = role {
 *         name = "admin"
 *         description = "Administrator"
 *         extends = user
 *         isAdmin = true
 *     }
 *
 *     // For multiple inheritance:
 *     val moderator = role {
 *         name = "moderator"
 *         inherits = setOf(user, contentReviewer)
 *     }
 * }
 *
 * // guest is hierarchy.firstRole / hierarchy.defaultRole
 * // Roles are implicitly ordered: guest < user < admin
 * ```
 */
@DslMarker
public annotation class RoleDsl

/**
 * Builder for creating role hierarchies.
 */
@RoleDsl
public class RoleHierarchyBuilder {
    private val roles = mutableMapOf<String, Role>()
    private val rolesList = mutableListOf<Role>()

    /**
     * Create a new role.
     *
     * @param block Configuration block for the role
     */
    public fun role(block: RoleBuilder.() -> Unit): Role {
        val builder = RoleBuilder()
        builder.block()
        val role = builder.build()
        require(role.name.isNotEmpty()) { "Role name must be specified" }
        require(role.name !in roles) { "Role '${role.name}' already exists" }
        roles[role.name] = role
        rolesList.add(role)
        return role
    }

    /**
     * Build the complete hierarchy.
     */
    public fun build(): RoleHierarchy = RoleHierarchy(roles, rolesList)

    /**
     * Get the created roles.
     */
    internal fun getRoles(): Map<String, Role> = roles
}

/**
 * Builder for individual roles.
 */
@RoleDsl
public class RoleBuilder {
    public var name: String = ""
    public var description: String? = null
    public var isAdmin: Boolean = false

    /**
     * Set the parent role(s) this role inherits from.
     * Can be a single role or multiple roles.
     */
    public var inherits: Set<Role> = emptySet()

    /**
     * Convenience property for single inheritance.
     * Setting this replaces any existing inheritance.
     */
    public var extends: Role?
        get() = inherits.singleOrNull()
        set(value) {
            inherits = value?.let { setOf(it) } ?: emptySet()
        }

    internal fun build(): Role = Role(
        name = name,
        description = description,
        inherits = inherits,
        builtinType = if (isAdmin) BuiltinRoleType.Admin() else BuiltinRoleType.Regular
    )
}

/**
 * Container for a role hierarchy.
 */
public class RoleHierarchy(
    private val roles: Map<String, Role>,
    private val orderedRoles: List<Role> = roles.values.toList()
) {
    /**
     * Get all roles in the hierarchy.
     */
    public fun getAllRoles(): Map<String, Role> = roles

    /**
     * Get a role by name.
     */
    public operator fun get(name: String): Role? = roles[name]

    /**
     * Get a role by index (in order of definition).
     */
    public operator fun get(index: Int): Role = orderedRoles[index]

    /**
     * Get all roles as a list.
     */
    public fun all(): List<Role> = orderedRoles

    /**
     * Find a role by predicate.
     */
    public fun find(predicate: (Role) -> Boolean): Role? = orderedRoles.find(predicate)

    /**
     * Get the first role defined (often used as default/guest).
     * Note: This is simply the first role in declaration order.
     */
    public val firstRole: Role
        get() = orderedRoles.firstOrNull() ?: error("No roles defined")

    /**
     * Get the default role (alias for firstRole).
     * Override this in your application if you need different default role logic.
     */
    public val defaultRole: Role
        get() = firstRole

    /**
     * Get the admin role.
     */
    public val admin: Role
        get() = orderedRoles.find { it.hasAdminPrivileges() } ?: error("No admin role defined")

    /**
     * Number of roles in the hierarchy.
     */
    public val size: Int get() = orderedRoles.size
}

/**
 * Result of building a role hierarchy.
 */
public data class RoleHierarchyResult(
    public val hierarchy: RoleHierarchy,
    public val roles: Map<String, Role>
) {
    /**
     * Get a role by name.
     */
    public operator fun get(name: String): Role = roles[name] ?: error("Role '$name' not found")
}

/**
 * Create a role hierarchy using the DSL.
 *
 * Example:
 * ```kotlin
 * val (hierarchy, roles) = roleHierarchy {
 *     val guest = role {
 *         name = "guest"
 *         description = "Guest user"
 *     }
 *     val user = role {
 *         name = "user"
 *         description = "Regular user"
 *         extends = guest
 *     }
 *     val admin = role {
 *         name = "admin"
 *         description = "Administrator"
 *         extends = user
 *         isAdmin = true
 *     }
 *
 *     // Return the roles for type-safe access
 *     RoleRefs(guest, user, admin)
 * }
 *
 * // Now you can access: roles.guest, roles.user, roles.admin
 * ```
 */
public fun <T> roleHierarchy(block: RoleHierarchyBuilder.() -> T): Pair<RoleHierarchy, T> {
    val builder = RoleHierarchyBuilder()
    val result = builder.block()
    return builder.build() to result
}

/**
 * Create a role hierarchy using the DSL (legacy version).
 */
public fun roleHierarchy(block: RoleHierarchyBuilder.() -> Unit): RoleHierarchy {
    val builder = RoleHierarchyBuilder()
    builder.block()
    return builder.build()
}

/**
 * Create a role hierarchy with type-safe access to defined roles.
 *
 * This version returns a custom result type that provides both the hierarchy
 * and direct access to each role defined in the builder.
 *
 * Example:
 * ```kotlin
 * class AppRoles(builder: RoleHierarchyBuilder) {
 *     val guest = builder.role {
 *         name = "guest"
 *         description = "Guest user"
 *     }
 *     val user = builder.role {
 *         name = "user"
 *         description = "Regular user"
 *         extends = guest
 *     }
 *     val admin = builder.role {
 *         name = "admin"
 *         description = "Administrator"
 *         extends = user
 *         isAdmin = true
 *     }
 * }
 *
 * val roles = roleHierarchyWithRoles(::AppRoles)
 * // Now you can access: roles.guest, roles.user, roles.admin
 * // And also: roles.hierarchy
 * ```
 */
public inline fun <reified T : Any> roleHierarchyWithRoles(
    crossinline factory: (RoleHierarchyBuilder) -> T
): T {
    val builder = RoleHierarchyBuilder()
    return factory(builder).also {
        // The hierarchy is built automatically when getRoles() is called
        builder.build()
    }
}

/**
 * Base class for type-safe role definitions.
 * Extend this class to define your application's roles using property delegates.
 *
 * Example:
 * ```kotlin
 * class MyAppRoles : Roles() {
 *     val guest by role {
 *         name = "guest"
 *         description = "Guest access"
 *     }
 *     val user by role {
 *         name = "user"
 *         extends = guest
 *     }
 *     val admin by role {
 *         name = "admin"
 *         extends = user
 *         isAdmin = true
 *     }
 * }
 *
 * val roles = MyAppRoles()
 * // Access: roles.guest, roles.user, roles.admin, roles.hierarchy
 * ```
 */
public abstract class Roles {
    private val builder = RoleHierarchyBuilder()
    private val roleProperties = mutableListOf<RoleProperty>()
    
    init {
        // Force initialization of all role properties
        ensureRolesInitialized()
    }
    
    private fun ensureRolesInitialized() {
        // This will be called after all property delegates are set up
        // but we need a different approach
    }
    
    /**
     * The role hierarchy created from the defined roles.
     * @throws IllegalStateException if hierarchy initialization fails
     */
    public val hierarchy: RoleHierarchy by lazy {
        try {
            // Force evaluation of all role properties
            roleProperties.forEach { it.initialize() }
            val hierarchy = builder.build()
            
            // Validate the hierarchy for cycles
            validateHierarchy(hierarchy)
            
            hierarchy
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialize role hierarchy: ${e.message}", e)
        }
    }

    /**
     * Validates the role hierarchy for circular dependencies.
     */
    private fun validateHierarchy(hierarchy: RoleHierarchy) {
        for (role in hierarchy.all()) {
            try {
                // This will throw if there's a cycle
                role.getAllInheritedRoles()
            } catch (e: IllegalStateException) {
                throw IllegalStateException("Invalid role hierarchy: ${e.message}", e)
            }
        }
    }

    /**
     * Define a role using property delegation.
     */
    protected fun role(block: RoleBuilder.() -> Unit): RoleProperty {
        val property = RoleProperty(builder, block)
        roleProperties.add(property)
        return property
    }
}

/**
 * Property delegate for role definitions.
 */
public class RoleProperty(
    private val builder: RoleHierarchyBuilder,
    private val block: RoleBuilder.() -> Unit
) : kotlin.properties.ReadOnlyProperty<Roles, Role> {
    
    private var role: Role? = null
    
    override fun getValue(thisRef: Roles, property: kotlin.reflect.KProperty<*>): Role {
        return role ?: run {
            val newRole = builder.role(block)
            role = newRole
            newRole
        }
    }
    
    /**
     * Force initialization of this role.
     */
    internal fun initialize() {
        if (role == null) {
            role = builder.role(block)
        }
    }
}

/**
 * Base class for type-safe role definitions (Legacy - use Roles instead).
 * Extend this class to define your application's roles.
 *
 * Example:
 * ```kotlin
 * class MyAppRoles(builder: RoleHierarchyBuilder) : RoleSet(builder) {
 *     val guest = role {
 *         name = "guest"
 *         description = "Guest access"
 *     }
 *     val user = role {
 *         name = "user"
 *         extends = guest
 *     }
 *     val admin = role {
 *         name = "admin"
 *         extends = user
 *         isAdmin = true
 *     }
 * }
 *
 * val roles = RoleSet.create(::MyAppRoles)
 * ```
 */
@Deprecated(
    "Use Roles base class instead for simpler syntax",
    ReplaceWith("Roles", "ai.koog.agents.core.tools.permissions.Roles"),
    DeprecationLevel.WARNING
)
public abstract class RoleSet(
    protected val builder: RoleHierarchyBuilder
) {
    /**
     * The role hierarchy created from the defined roles.
     */
    public val hierarchy: RoleHierarchy by lazy { builder.build() }

    /**
     * Define a role.
     */
    protected fun role(block: RoleBuilder.() -> Unit): Role {
        return builder.role(block)
    }

    public companion object {
        /**
         * Create an instance of a RoleSet subclass.
         *
         * Example:
         * ```kotlin
         * val roles = MyAppRoles.create()
         * ```
         */
        @Suppress("UNCHECKED_CAST")
        @Deprecated(
            "Use Roles base class instead: class MyRoles : Roles() { ... }; val roles = MyRoles()",
            ReplaceWith("MyRoles()", "ai.koog.agents.core.tools.permissions.Roles"),
            DeprecationLevel.WARNING
        )
        public fun <T : RoleSet> create(factory: (RoleHierarchyBuilder) -> T): T {
            val builder = RoleHierarchyBuilder()
            return factory(builder)
        }
    }
}
