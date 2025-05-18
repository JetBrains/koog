package ai.koog.agents.example.memory.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

/**
 * Diagnostic tools for customer support.
 * Provides capabilities for:
 * - Running diagnostics on devices
 * - Analyzing error codes
 * - Retrieving diagnostic history
 * - Providing troubleshooting steps
 */
@LLMDescription("Tools for performing diagnostics and troubleshooting for customer support")
class DiagnosticToolSet : ToolSet {

    @Tool
    @LLMDescription("Run diagnostic on a device")
    fun runDiagnostic(
        @LLMDescription("The ID of the device")
        deviceId: String,

        @LLMDescription("Additional information for the diagnostic")
        additionalInfo: String = ""
    ): String {
        // In a real implementation, this would run actual diagnostics
        return """
            Diagnostic Results for Device $deviceId:
            - Connection Status: OK
            - Memory Usage: 65%
            - CPU Load: Normal
            - Storage Space: 45% used
            - Network Latency: 35ms

            No critical issues detected.
            Additional checks based on info "$additionalInfo" completed successfully.
        """.trimIndent()
    }

    @Tool
    @LLMDescription("Analyze an error code")
    fun analyzeError(
        @LLMDescription("The error code to analyze")
        errorCode: String
    ): String {
        // In a real implementation, this would look up error codes in a database
        return when (errorCode) {
            "E1001" -> "Error E1001: Network Connection Failure - Check network settings and router connection."
            "E2002" -> "Error E2002: Authentication Failure - Verify credentials and try again."
            "E3003" -> "Error E3003: Resource Unavailable - The requested resource is temporarily unavailable."
            else -> "Unknown Error Code: $errorCode - No information available for this error code."
        }
    }

    @Tool
    @LLMDescription("Get diagnostic history for a device")
    fun getDiagnosticHistory(
        @LLMDescription("The ID of the device")
        deviceId: String
    ): String {
        // In a real implementation, this would retrieve data from a database
        return """
            Diagnostic History for Device $deviceId:
            1. 2023-09-15: Full system diagnostic - All systems normal
            2. 2023-08-22: Network connectivity check - Intermittent connection detected, router reset recommended
            3. 2023-07-10: Memory usage analysis - High memory usage detected, application restart recommended
            4. 2023-06-05: Storage space check - Low storage warning, cleanup recommended
        """.trimIndent()
    }

    @Tool
    @LLMDescription("Get troubleshooting steps for an issue")
    fun getTroubleshootingSteps(
        @LLMDescription("The error code to get troubleshooting steps for")
        errorCode: String,

        @LLMDescription("The ID of the device (optional)")
        deviceId: String = ""
    ): String {
        // In a real implementation, this would retrieve data from a knowledge base
        val deviceSpecificInfo = if (deviceId.isNotEmpty()) {
            "\nDevice-specific recommendations for $deviceId: Ensure latest firmware is installed."
        } else {
            ""
        }

        return when (errorCode) {
            "E1001" -> """
                Troubleshooting Steps for Error E1001 (Network Connection Failure):
                1. Check physical network connections
                2. Restart your router and modem
                3. Verify network settings are correct
                4. Disable VPN or proxy if in use
                5. Contact your ISP if problem persists$deviceSpecificInfo
            """.trimIndent()

            "E2002" -> """
                Troubleshooting Steps for Error E2002 (Authentication Failure):
                1. Verify username and password are correct
                2. Check if caps lock is enabled
                3. Reset password if necessary
                4. Ensure account is not locked
                5. Contact administrator if problem persists$deviceSpecificInfo
            """.trimIndent()

            "E3003" -> """
                Troubleshooting Steps for Error E3003 (Resource Unavailable):
                1. Wait a few minutes and try again
                2. Check system status page for outages
                3. Clear browser cache and cookies
                4. Try accessing from a different network
                5. Contact support if problem persists$deviceSpecificInfo
            """.trimIndent()

            else -> "No troubleshooting steps available for error code: $errorCode$deviceSpecificInfo"
        }
    }
}
