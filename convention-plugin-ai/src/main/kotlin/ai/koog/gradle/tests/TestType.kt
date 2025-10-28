package ai.koog.gradle.tests

import org.gradle.api.tasks.testing.TestFilter

enum class TestType(
    internal val namePattern: String,
    val shortName: String,
    val parallelism: Boolean,
    internal val maxHeapForJvm: String? = null
) {
    DEFAULT("", "test", parallelism = true),
    INTEGRATION("*.integration_*", "integration", parallelism = true),
    OLLAMA("*.ollama_*", "ollama", parallelism = false);

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
