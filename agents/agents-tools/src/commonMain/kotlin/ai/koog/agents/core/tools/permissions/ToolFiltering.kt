package ai.koog.agents.core.tools.permissions

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry

/**
 * Filters tools based on the current role's permissions.
 *
 * This function checks each tool's policy against the provided role and returns only
 * the tools that the role has permission to access.
 *
 * @param tools The list of tools to filter
 * @param toolRegistry The registry containing tool policies
 * @param role The current role to check permissions against
 * @param permissionChecker The permission checker to use (optional, defaults to StandardPermissionChecker)
 * @return A filtered list of tools that the role has permission to use
 */
public fun filterToolsByRole(
    tools: List<Tool<*, *>>,
    toolRegistry: ToolRegistry,
    role: Role?,
    permissionChecker: PermissionChecker = StandardPermissionChecker()
): List<Tool<*, *>> {
    // If no role is provided, return all tools (backward compatibility)
    if (role == null) return tools

    return tools.filter { tool ->
        val policy = toolRegistry.getToolPolicy(tool)

        // If no policy is defined, the tool is accessible to all
        if (policy == null) return@filter true

        // Check permission
        val result = permissionChecker.checkPermission(role, policy)
        result is PermissionCheckResult.Granted
    }
}

/**
 * Filters tool descriptors based on the current role's permissions.
 *
 * @param descriptors The list of tool descriptors to filter
 * @param toolRegistry The registry containing tool policies
 * @param role The current role to check permissions against
 * @param permissionChecker The permission checker to use (optional, defaults to StandardPermissionChecker)
 * @return A filtered list of tool descriptors that the role has permission to use
 */
public fun filterToolDescriptorsByRole(
    descriptors: List<ToolDescriptor>,
    toolRegistry: ToolRegistry,
    role: Role?,
    permissionChecker: PermissionChecker = StandardPermissionChecker()
): List<ToolDescriptor> {
    // If no role is provided, return all descriptors (backward compatibility)
    if (role == null) return descriptors

    return descriptors.filter { descriptor ->
        val policy = toolRegistry.getToolPolicyByName(descriptor.name)

        // If no policy is defined, the tool is accessible to all
        if (policy == null) return@filter true

        // Check permission
        val result = permissionChecker.checkPermission(role, policy)
        result is PermissionCheckResult.Granted
    }
}
