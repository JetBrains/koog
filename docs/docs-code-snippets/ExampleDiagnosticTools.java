import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;


public class ExampleDiagnosticTools {

    @LLMDescription(description = "Tools for performing diagnostics and troubleshooting on devices")
    public class DiagnosticToolSet implements ToolSet {
        // Convenience overload (not exposed as a tool)
        public String runDiagnostic(String deviceId) {
            return runDiagnostic(deviceId, "");
        }

        @Tool
        @LLMDescription(description = "Run diagnostic on a device to check its status and identify any issues")
        public String runDiagnostic(
                @LLMDescription(description = "The ID of the device to diagnose") String deviceId,
                @LLMDescription(description = "Additional information for the diagnostic (optional)") String additionalInfo
        ) {
            // Implementation
            return "Diagnostic results for device " + deviceId;
        }

        @Tool
        @LLMDescription(description = "Analyze an error code to determine its meaning and possible solutions")
        public String analyzeError(
                @LLMDescription(description = "The error code to analyze (e.g., 'E1001')") String errorCode
        ) {
            // Implementation
            return "Analysis of error code " + errorCode;
        }
    }
}