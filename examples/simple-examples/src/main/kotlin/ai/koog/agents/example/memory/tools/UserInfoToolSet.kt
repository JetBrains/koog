package ai.koog.agents.example.memory.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

/**
 * User information tools for customer support.
 * Provides capabilities for:
 * - Retrieving user preferences and communication style
 * - Accessing user issue history
 * - Managing user contact information
 */
@LLMDescription("Tools for retrieving and managing user information for customer support")
class UserInfoToolSet : ToolSet {

    @Tool
    @LLMDescription("Get user communication preferences")
    fun getUserPreferences(
        @LLMDescription("The ID of the user")
        userId: String
    ): String {
        // In a real implementation, this would retrieve data from a database
        return """
            User Preferences for $userId:
            - Preferred communication style: Technical
            - Response length preference: Detailed
            - Preferred contact method: Email
            - Technical expertise level: Advanced
        """.trimIndent()
    }

    @Tool
    @LLMDescription("Get user's past issues")
    fun getUserIssueHistory(
        @LLMDescription("The ID of the user")
        userId: String
    ): String {
        // In a real implementation, this would retrieve data from a database
        return """
            Issue History for $userId:
            1. Issue #12345 - "Login problem" - Resolved on 2023-05-15
               Solution: Reset password and updated security settings
            2. Issue #12789 - "Configuration error" - Resolved on 2023-07-22
               Solution: Updated configuration file and restarted service
            3. Issue #13256 - "Performance degradation" - Open since 2023-09-10
               Status: Under investigation by technical team
        """.trimIndent()
    }

    @Tool
    @LLMDescription("Get user's contact information")
    fun getUserContactInfo(
        @LLMDescription("The ID of the user")
        userId: String
    ): String {
        // In a real implementation, this would retrieve data from a database
        return """
            Contact Information for $userId:
            - Email: user@example.com
            - Phone: +1-555-123-4567
            - Organization: Acme Corp
            - Department: IT Support
        """.trimIndent()
    }

    @Tool
    @LLMDescription("Update user's communication preferences")
    fun updateUserPreferences(
        @LLMDescription("The ID of the user")
        userId: String,

        @LLMDescription("New preferences information")
        preferences: String
    ): String {
        // In a real implementation, this would update a database
        return "Successfully updated preferences for user $userId: $preferences"
    }
}
