package ai.koog.agents.example.memory.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

/**
 * Knowledge base tools for customer support.
 * Provides capabilities for:
 * - Searching for solutions in the knowledge base
 * - Retrieving product information
 * - Accessing organization-specific solutions
 * - Storing new solutions
 */
@LLMDescription("Tools for accessing and managing the knowledge base for customer support")
class KnowledgeBaseToolSet : ToolSet {

    @Tool
    @LLMDescription("Search for solutions by keywords")
    fun searchSolutions(
        @LLMDescription("Search query or keywords")
        query: String
    ): String {
        // In a real implementation, this would search a knowledge base
        val keywords = query.lowercase().split(" ")
        val results = mutableListOf<String>()

        if (keywords.any { it in listOf("network", "connection", "wifi", "internet") }) {
            results.add("Solution #1001: Troubleshooting Network Connectivity Issues")
        }

        if (keywords.any { it in listOf("login", "password", "authentication", "access") }) {
            results.add("Solution #1002: Resolving Login and Authentication Problems")
        }

        if (keywords.any { it in listOf("slow", "performance", "speed", "lag") }) {
            results.add("Solution #1003: Improving System Performance")
        }

        if (keywords.any { it in listOf("error", "crash", "freeze", "unresponsive") }) {
            results.add("Solution #1004: Handling Application Crashes and Freezes")
        }

        return if (results.isNotEmpty()) {
            "Search Results for '$query':\n" + results.joinToString("\n")
        } else {
            "No solutions found for query: $query"
        }
    }

    @Tool
    @LLMDescription("Get information about a product")
    fun getProductInfo(
        @LLMDescription("The ID of the product")
        productId: String
    ): String {
        // In a real implementation, this would retrieve product data from a database
        return when (productId) {
            "P1001" -> """
                Product Information for P1001 (Enterprise CRM):
                - Version: 4.2.1
                - Release Date: 2023-06-15
                - Support Status: Active
                - Key Features: Contact Management, Sales Pipeline, Reporting Dashboard
                - Known Issues: Occasional slow performance with large datasets
            """.trimIndent()
            "P1002" -> """
                Product Information for P1002 (Cloud Storage Solution):
                - Version: 2.8.5
                - Release Date: 2023-08-22
                - Support Status: Active
                - Key Features: File Sync, Sharing, Encryption, Mobile Access
                - Known Issues: Upload issues with files larger than 2GB
            """.trimIndent()
            "P1003" -> """
                Product Information for P1003 (Security Suite):
                - Version: 5.1.0
                - Release Date: 2023-07-10
                - Support Status: Active
                - Key Features: Firewall, Antivirus, Intrusion Detection, VPN
                - Known Issues: Occasional false positives with certain file types
            """.trimIndent()
            else -> "No product information found for product ID: $productId"
        }
    }

    @Tool
    @LLMDescription("Get solutions specific to an organization")
    fun getOrganizationSolutions(
        @LLMDescription("The ID of the organization")
        organizationId: String,

        @LLMDescription("The ID of the product (optional)")
        productId: String = ""
    ): String {
        // In a real implementation, this would retrieve organization-specific solutions
        val productFilter = if (productId.isNotEmpty()) {
            " for product $productId"
        } else {
            ""
        }

        return when (organizationId) {
            "O1001" -> """
                Organization-specific Solutions for O1001 (Acme Corp)$productFilter:
                1. Custom authentication integration with Acme Corp's SSO system
                2. Specialized reporting dashboard for Acme Corp's sales team
                3. Custom data migration process for legacy Acme Corp systems
            """.trimIndent()
            "O1002" -> """
                Organization-specific Solutions for O1002 (TechGlobal)$productFilter:
                1. High-volume data processing configuration for TechGlobal's needs
                2. Custom API integration with TechGlobal's internal systems
                3. Enhanced security protocols specific to TechGlobal's requirements
            """.trimIndent()
            "O1003" -> """
                Organization-specific Solutions for O1003 (HealthCare Inc)$productFilter:
                1. HIPAA-compliant data handling procedures
                2. Custom patient record integration
                3. Specialized audit logging for healthcare compliance
            """.trimIndent()
            else -> "No organization-specific solutions found for organization ID: $organizationId$productFilter"
        }
    }

    @Tool
    @LLMDescription("Store a new solution in the knowledge base")
    fun storeSolution(
        @LLMDescription("The ID of the product (optional)")
        productId: String = "",

        @LLMDescription("The ID of the organization (optional)")
        organizationId: String = "",

        @LLMDescription("Data for the solution to store")
        solutionData: String
    ): String {
        // In a real implementation, this would store the solution in a database
        val targetInfo = if (organizationId.isNotEmpty()) {
            "organization $organizationId"
        } else if (productId.isNotEmpty()) {
            "product $productId"
        } else {
            "general knowledge base"
        }

        return "Successfully stored solution in the $targetInfo:\n$solutionData"
    }
}
