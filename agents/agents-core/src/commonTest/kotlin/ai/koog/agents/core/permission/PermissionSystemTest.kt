package ai.koog.agents.core.permission

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.testing.TestRoles
import ai.koog.agents.testing.TestTools
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PermissionSystemTest {


    @Test
    fun testBasicRoles() {
        // Use shared test roles
        val guest = TestRoles.guest
        val user = TestRoles.user
        val admin = TestRoles.admin

        // Test role hierarchy
        assertTrue(user.hasRole(guest))
        assertTrue(admin.hasRole(user))
        assertTrue(admin.hasRole(guest))
        assertTrue(admin.hasAdminPrivileges())
        assertFalse(user.hasAdminPrivileges())
    }

    @Test
    fun testToolPermissions() = runTest {
        // Use shared test roles
        val user = TestRoles.user
        val admin = TestRoles.admin

        // Create tool registry with permissions using DSL
        val toolRegistry = ToolRegistry {
            // Read is allowed for everyone (no permission set)
            tool(TestTools.ReadTool())

            // Write requires User role
            tool(TestTools.WriteTool()) {
                minimumRole = user
            }
        }

        // Verify permissions were set
        val writePolicy = toolRegistry.getToolPolicyByName("write")
        assertNotNull(writePolicy)
        val requirement = writePolicy.roleRequirement
        assertTrue(requirement is RoleRequirement.MinimumRole)
        assertEquals(user, requirement.role)
    }

    // Removed testApprovalHandler - approval functionality was not implemented in this PR

    // Note: Full integration test would require creating an AIAgent
    // with mocked LLM responses and testing the complete flow.

    @Test
    fun testRoleHierarchy() {
        // Use shared test role hierarchy
        val hierarchy = TestRoles.createRoleHierarchy()
        val guest = TestRoles.guest
        val user = TestRoles.user
        val admin = TestRoles.admin

        // Create config with role hierarchy
        val config = AIAgentConfig(
            prompt = ai.koog.prompt.dsl.prompt("test-prompt") { system { "test" } },
            model = ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 3,
            roleHierarchy = hierarchy
        )

        // Verify hierarchy is properly configured
        assertNotNull(config.roleHierarchy)
        assertEquals(hierarchy, config.roleHierarchy)

        // Test role inheritance
        assertTrue(admin.hasRole(user))
        assertTrue(admin.hasRole(guest))
        assertTrue(user.hasRole(guest))
        assertFalse(guest.hasRole(user))
    }
}
