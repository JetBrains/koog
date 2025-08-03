# Tool Permissions and Policies

This package provides tool governance for Koog agents, including permissions, rate limiting, and caching.

## Features

- **Role-based access control** with hierarchical inheritance
- **Tool permission management** with minimum role requirements
- **Rate limiting** with role-based limits
- **Tool result caching** with TTL and extensible cache key strategies
- **Event emission** for tool governance decisions

## Core Concepts

### Roles

Roles define the access level of an agent within the system:

```kotlin
// Define type-safe roles
class AppRoles(builder: RoleHierarchyBuilder) : RoleSet(builder) {
    val guest = role {
        name = "guest"
        description = "Basic read-only access"
    }
    
    val user = role {
        name = "user"
        description = "Standard user access"
        extends = guest  // Clean property-based inheritance
    }
    
    val admin = role {
        name = "admin"
        description = "Full administrative access"
        extends = user
        isAdmin = true
    }
}

// Create roles with full type safety
val roles = RoleSet.create(::AppRoles)

// Access roles directly: roles.guest, roles.user, roles.admin
// Get hierarchy: roles.hierarchy
```

### Tool Policies

Define tool policies when registering tools:

```kotlin
val toolRegistry = ToolRegistry {
    // Public tool - no restrictions
    tool(SearchTool())
    
    // Hierarchical permissions - user role and above can access
    tool(UpdateTool()) {
        minimumRole = roles.user  // Allows user, admin (via hierarchy)
        
        rateLimits {
            role(roles.user) limit 10 per 1.minutes
            role(roles.admin) limit 100 per 1.minutes
            // By default includes args in rate limit keys
            // excludeArgs()  // Would rate limit all updates regardless of what's being updated
        }
        
        cache {
            enabled = true
            ttl = 5.minutes
            // By default includes args in cache keys
            excludeArgs()  // Cache update results regardless of specific data
            roleSpecific()  // But do cache separately per role
        }
    }
    
    // Explicit role list - only specific roles allowed (no hierarchy)
    tool(ModeratorTool()) {
        allowedRoles = setOf(roles.moderator, roles.admin)  // ONLY these roles
        
        rateLimits {
            role(roles.moderator) limit 50 per 1.minutes
            role(roles.admin) limit 100 per 1.minutes
        }
    }
    
    // Admin-only tool using hierarchy
    tool(DeleteTool()) {
        minimumRole = roles.admin
    }
}
```

#### Permission Types

The permission system supports two approaches:

1. **Hierarchical (`minimumRole`)**: Checks if the user has the required role or any role that inherits from it
   - Use when you want roles to inherit permissions from parent roles
   - Example: If `admin` inherits from `user`, setting `minimumRole = user` allows both `user` and `admin`

2. **Explicit List (`allowedRoles`)**: Only allows the exact roles specified (no inheritance)
   - Use when you want strict role control without inheritance
   - Example: `allowedRoles = setOf(moderatorRole, supportRole)` only allows those specific roles

## Integration with AIAgent

The permission system is integrated directly into AIAgent's tool execution:

```kotlin
val agent = AIAgent(
    promptExecutor = executor,
    strategy = strategy,
    agentConfig = AIAgentConfig(
        prompt = prompt,
        model = model,
        
        // Role configuration
        roleHierarchy = roleHierarchy,
        
        // Optional governance components
        permissionChecker = DefaultPermissionChecker(),
        rateLimiter = InMemoryRateLimiter(),
        toolCache = InMemoryToolCache()
    ),
    toolRegistry = toolRegistry
)
```

### Runtime Role Switching

For production scenarios with agent pools, you can execute the same agent instance with different roles:

```kotlin
// Create a single agent instance (e.g., from a pool)
val agent = createAgent()

// Execute with different roles for different users
val userResult = agent.run("Process user data", role = userRole)
val adminResult = agent.run("Process admin data", role = adminRole)

// When no role is specified, StandardRoles.defaultRole is used
val defaultResult = agent.run("Process public data")
```

This is ideal for:
- **Agent pools**: Reuse agent instances across multiple users
- **Multi-tenant systems**: Switch roles based on the current user
- **Dynamic permissions**: Change permissions without recreating agents

## Event Handling

Listen for governance events using the event handler feature:

```kotlin
agent.install(EventHandler) {
    onToolPermissionDenied { context ->
        logger.warn("Tool ${context.tool.name} denied for role ${context.actualRole}")
    }
    
    onToolRateLimitExceeded { context ->
        logger.warn("Rate limit exceeded for ${context.tool.name}")
    }
    
    onToolCacheHit { context ->
        logger.debug("Cache hit for ${context.tool.name}")
    }
}
```

## Cache Key Customization

The caching system is designed to be extensible. You can implement custom cache key generators:

```kotlin
// Custom cache key generator that includes session info
// Additional context is passed via constructor - type safe!
class SessionAwareCacheKeyGenerator(
    private val sessionId: String
) : CacheKeyGenerator {
    override fun generateKey(context: CacheKeyContext): String {
        return "tool:${context.tool.name}:session:$sessionId:args:${context.toolArgs.hashCode()}"
    }
}

// For more complex scenarios with type-safe user context
class UserAwareCacheKeyGenerator(
    private val userProvider: () -> User  // Type-safe user provider
) : CacheKeyGenerator {
    override fun generateKey(context: CacheKeyContext): String {
        val user = userProvider()
        return "tool:${context.tool.name}:user:${user.id}:tenant:${user.tenantId}:args:${context.toolArgs.hashCode()}"
    }
}

// Use in tool configuration
tool(MyTool()) {
    cache {
        enabled = true
        ttl = 10.minutes
        keyGenerator = SessionAwareCacheKeyGenerator(sessionId)
    }
}

// Time-window based caching for rate-sensitive operations
class TimeWindowCacheKeyGenerator(
    private val windowSize: Duration = 1.hours
) : CacheKeyGenerator {
    override fun generateKey(context: CacheKeyContext): String {
        val window = Clock.System.now().toEpochMilliseconds() / windowSize.inWholeMilliseconds
        return "tool:${context.tool.name}:window:$window:args:${context.toolArgs.hashCode()}"
    }
}
```

### Built-in Cache Key Generator

The `DefaultCacheKeyGenerator` provides common cache key strategies:

```kotlin
// Basic caching (tool name + args)
keyGenerator = DefaultCacheKeyGenerator()

// Include role in cache key
keyGenerator = DefaultCacheKeyGenerator(includeRole = true)

// Include agent ID but not role
keyGenerator = DefaultCacheKeyGenerator(includeRole = false, includeAgent = true)

// Custom args hasher for complex arguments
keyGenerator = DefaultCacheKeyGenerator(
    argsHasher = { args -> args.toStableString() }
)
```

## Utility Functions

```kotlin
// Generate a rate limit key
val rateLimitKey = generateRateLimitKey(
    toolName = tool.name,
    effectiveRole = role
)
```

## Handling Permission Denials

The permission system automatically checks permissions during tool execution. If a user lacks permission, a `PermissionDeniedException` is thrown. This ensures security by default.

### Simple Fallback Patterns

For graceful handling of permission denials, use Kotlin's standard exception handling or the provided utility functions:

```kotlin
// Using try-catch in nodes
val smartNode = node<String, String> { input ->
    try {
        // Try primary tool
        llm.writeSession {
            requestLLMForceOneTool(AdminTool())
        }.firstText()
    } catch (e: PermissionDeniedException) {
        // Fallback to user tool
        llm.writeSession {
            user("Using limited access...")
            requestLLMForceOneTool(UserTool())
        }.firstText()
    }
}

// Using utility functions
val fallbackNode = node<String, Message.Response> { input ->
    llm.writeSession {
        user(input)
        // Automatically falls back to UserTool if AdminTool is denied
        requestWithFallback(
            preferred = AdminTool(),
            fallback = UserTool()
        )
    }
}

// Try tool execution without throwing
val tryNode = node<Message.Tool.Call, String> { toolCall ->
    val result = tryToolCall(toolCall)
        ?: return@node "Tool execution was denied"
    
    result.content
}
```

### Checking Permissions

Within any node execution context, you can check permissions before attempting operations:

```kotlin
val conditionalNode = node<String, String>("conditional-action") { input ->
    if (hasPermissionForTool<AdminTool>()) {
        // Use admin tool
        llm.writeSession {
            requestLLMForceOneTool(AdminTool())
        }.firstText()
    } else {
        // Fallback behavior
        "Limited access: $input"
    }
}

// Check by tool name
val canDelete = hasPermissionForTool("admin-delete")
```

### Node-Level Permissions

You can set permissions on custom nodes using the existing `permissionMetadata`:

```kotlin
strategy {
    val adminNode = node<String, String>("admin-action") { input ->
        // This code only executes if user has admin role
        processAdminAction(input)
    }
    
    // Set permission requirements - will throw exception if denied
    adminNode.permissionMetadata = PermissionMetadata(
        minimumRole = adminRole
    )
}
```

## Core Integration

The permission system is integrated into Koog's core:

- **Tool Execution**: `AIAgent.processToolCall()` automatically checks permissions before executing tools
- **Node Execution**: `AIAgentNode.execute()` checks `permissionMetadata` before running node logic
- **Tool Filtering**: `AIAgentSubgraph` filters available tools based on the current role
- **Context Support**: `AIAgentContextBase` provides `hasPermission()`, `hasRole()`, and `currentRoles`
- **Utility Functions**: Helper extensions for common patterns like fallbacks and permission checking

This integration ensures consistent permission checking throughout the framework while maintaining backward compatibility.

## Architecture Decisions

### Separation of Caching and Rate Limiting

While rate limiting could theoretically be implemented as an extension of caching (storing counters instead of results), we've kept them separate for several reasons:

1. **Clear Intent**: Separate interfaces make the purpose clear - `ToolCache` for results, `RateLimiter` for request counting
2. **Different Semantics**: Caches store and retrieve data, rate limiters track and enforce limits
3. **Different Patterns**: Cache uses get/put with TTL, rate limiting uses check-and-increment with windows
4. **Flexibility**: Users can swap implementations independently (e.g., Redis for cache, in-memory for rate limits)

Both systems share similar patterns:
- Key generation strategies (`CacheKeyGenerator` and `RateLimitKeyStrategy`)
- Context objects with tool, args, role, and agent information
- Optional inclusion of args, role, and agent in keys

Future versions might provide a unified storage abstraction, but the current design prioritizes clarity and simplicity.

## Design Principles

This permissions system has been designed with simplicity and extensibility in mind:

1. **No Over-Engineering**: We avoid complex abstractions and callback-based approaches. Standard Kotlin exception handling and simple utility functions provide all the flexibility needed.

2. **Type Safety**: The system avoids `Any` types and string-based configuration where possible. Custom context is passed through constructor parameters rather than untyped maps.

3. **Clean DSL**: The role hierarchy uses intuitive property-based syntax (`extends = parentRole`) rather than method calls, and avoids numeric priorities in favor of declaration order.

4. **Extensibility**: Core interfaces like `CacheKeyGenerator` allow for custom implementations without modifying the framework.

5. **Event-Driven**: Rather than building metrics into the cache or rate limiter, the system emits events that can be consumed by monitoring systems.

6. **Simplicity First**: Use standard Kotlin patterns (try-catch, nullable returns) rather than introducing new result types or node variants.

## Architectural Approach

The permission system is designed as a core part of Koog rather than an add-on:

### What's Already Integrated

1. **Node-level permissions**: `AIAgentNode` has `permissionMetadata` property and checks permissions in `execute()`
2. **Context-level support**: `AIAgentContextBase` has `hasPermission()`, `hasRole()`, `currentRoles`, and `roleHierarchy`
3. **Tool execution**: `AIAgent.processToolCall()` checks permissions before executing tools
4. **Tool filtering**: `AIAgentSubgraph` filters tools by role permissions

### Implementation Philosophy

Instead of adding special APIs or callbacks, we leverage standard Kotlin patterns:

```kotlin
// Simple try-catch in nodes
val node = node<String, String> { input ->
    try {
        // Try primary approach
        executeTool(AdminTool(), args)
    } catch (e: PermissionDeniedException) {
        // Fallback
        executeTool(UserTool(), args)
    }
}
```

This approach:
- Maintains backward compatibility (exceptions by default)
- Uses familiar Kotlin patterns
- Provides maximum flexibility
- Avoids over-abstraction

### Future Evolution

As usage patterns emerge, we may add:
- Additional utility functions for common patterns
- Permission-aware edge transformers
- Enhanced debugging/monitoring capabilities

The key is to start simple and let real usage drive API evolution.