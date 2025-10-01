package ai.koog.test.utils

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.DockerClientFactory

/**
 * Helper test condition method to skip test suite if Docker is not available.
 */
public class DockerAvailableCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        return try {
            DockerClientFactory.instance().client()
            ConditionEvaluationResult.enabled("Docker is available")
        } catch (e: Exception) {
            ConditionEvaluationResult.disabled("Docker is not available, skipping this test")
        }
    }
}
