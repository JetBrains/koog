package ai.koog.gradle.tests

import org.gradle.api.tasks.testing.TestFilter

enum class TestType(
    internal val namePattern: String,
    val shortName: String,
    val parallelism: Boolean = true,
    internal val maxHeapForJvm: String? = null
) {
    DEFAULT("", "test"),
    INTEGRATION("*.integration_*", "integration"),
    OLLAMA("*.ollama_*", "ollama");

    companion object {
        internal val testTypesWithoutMain = values().asList().minus(DEFAULT)
    }
}

internal fun TestFilter.configureFilter(testType: TestType) {
    isFailOnNoMatchingTests = false
    if (testType == TestType.DEFAULT) {
        for (otherType in TestType.testTypesWithoutMain) {
            excludeTestsMatching(otherType.namePattern)
        }
    } else {
        includeTestsMatching(testType.namePattern)
    }
}
