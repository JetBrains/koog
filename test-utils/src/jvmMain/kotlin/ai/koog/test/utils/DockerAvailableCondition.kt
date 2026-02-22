package ai.koog.test.utils

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.DockerClientFactory

/**
 * Helper test condition method to skip test suite if Docker is not available.
 */
public class DockerAvailableCondition : ExecutionCondition {

    private companion object {
        private val isAvailable: ConditionEvaluationResult by lazy {
            try {
                DockerClientFactory.instance().client()
                ConditionEvaluationResult.enabled("Docker is available")
            } catch (_: Exception) {
                ConditionEvaluationResult.disabled("Docker is not available, skipping this test")
            }
        }
    }

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult = isAvailable
}
