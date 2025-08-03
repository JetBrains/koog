package ai.koog.agents.core.permission

import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.testing.TestRoles
import ai.koog.agents.testing.TestTools
import kotlin.test.*

class ToolFilteringTest {


    @Test
    fun testToolFilteringByRole() {
        // Use shared test roles
        val guest = TestRoles.guest
        val user = TestRoles.user
        val admin = TestRoles.admin

        // Create tool registry with permissions
        val publicTool = TestTools.PublicTool()
        val userTool = TestTools.UserTool()
        val adminTool = TestTools.AdminTool()

        val toolRegistry = ToolRegistry {
            tool(publicTool) // No restrictions

            tool(userTool) {
                minimumRole = user
            }

            tool(adminTool) {
                minimumRole = admin
            }
        }

        // Test filtering for guest role
        val guestTools = filterToolsByRole(
            tools = toolRegistry.tools,
            toolRegistry = toolRegistry,
            role = guest
        )
        assertEquals(1, guestTools.size)
        assertTrue(guestTools.contains(publicTool))

        // Test filtering for user role
        val userTools = filterToolsByRole(
            tools = toolRegistry.tools,
            toolRegistry = toolRegistry,
            role = user
        )
        assertEquals(2, userTools.size)
        assertTrue(userTools.contains(publicTool))
        assertTrue(userTools.contains(userTool))
        assertFalse(userTools.contains(adminTool))

        // Test filtering for admin role
        val adminTools = filterToolsByRole(
            tools = toolRegistry.tools,
            toolRegistry = toolRegistry,
            role = admin
        )
        assertEquals(3, adminTools.size)
        assertTrue(adminTools.contains(publicTool))
        assertTrue(adminTools.contains(userTool))
        assertTrue(adminTools.contains(adminTool))
    }

    @Test
    fun testToolFilteringWithAllowedRoles() {
        // Use shared test roles
        val user = TestRoles.user
        val moderator = TestRoles.moderator
        val admin = TestRoles.admin

        // Create tool that only allows specific roles (no hierarchy)
        val specialTool = TestTools.PublicTool() // Reusing PublicTool class

        val toolRegistry = ToolRegistry {
            tool(specialTool) {
                allowedRoles = setOf(moderator, admin)
            }
        }

        // Test that user cannot access
        val userTools = filterToolsByRole(
            tools = toolRegistry.tools,
            toolRegistry = toolRegistry,
            role = user
        )
        assertEquals(0, userTools.size)

        // Test that moderator can access
        val moderatorTools = filterToolsByRole(
            tools = toolRegistry.tools,
            toolRegistry = toolRegistry,
            role = moderator
        )
        assertEquals(1, moderatorTools.size)

        // Test that admin can access
        val adminTools = filterToolsByRole(
            tools = toolRegistry.tools,
            toolRegistry = toolRegistry,
            role = admin
        )
        assertEquals(1, adminTools.size)
    }

    @Test
    fun testToolDescriptorFiltering() {
        // Use shared test roles
        val user = TestRoles.user
        val admin = TestRoles.admin

        // Create tool registry
        val toolRegistry = ToolRegistry {
            tool(TestTools.PublicTool())

            tool(TestTools.UserTool()) {
                minimumRole = user
            }

            tool(TestTools.AdminTool()) {
                minimumRole = admin
            }
        }

        val allDescriptors = toolRegistry.tools.map { it.descriptor }

        // Test filtering descriptors for user role
        val userDescriptors = filterToolDescriptorsByRole(
            descriptors = allDescriptors,
            toolRegistry = toolRegistry,
            role = user
        )

        assertEquals(2, userDescriptors.size)
        assertTrue(userDescriptors.any { it.name == "public_tool" })
        assertTrue(userDescriptors.any { it.name == "user_tool" })
        assertFalse(userDescriptors.any { it.name == "admin_tool" })
    }

    @Test
    fun testNullRoleReturnsAllTools() {
        // Create tool registry with restrictions
        val toolRegistry = ToolRegistry {
            tool(TestTools.PublicTool())
            tool(TestTools.UserTool()) {
                minimumRole = TestRoles.user
            }
            tool(TestTools.AdminTool()) {
                minimumRole = TestRoles.admin
            }
        }

        // Test that null role returns all tools (backward compatibility)
        val allTools = filterToolsByRole(
            tools = toolRegistry.tools,
            toolRegistry = toolRegistry,
            role = null
        )

        assertEquals(3, allTools.size)
    }
}
