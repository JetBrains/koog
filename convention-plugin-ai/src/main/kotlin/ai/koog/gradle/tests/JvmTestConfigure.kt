package ai.koog.gradle.tests

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * Common test configuration that can be applied to any Test task
 */
private fun Test.configureCommonTestSettings(
    testType: TestType = TestType.DEFAULT,
    maxHeap: String? = null
) {
    useJUnitPlatform()
    filter { configureFilter(testType) }
    group = "verification"

    val heapSize = maxHeap ?: testType.maxHeapForJvm
    heapSize?.let { maxHeapSize = it }

    if (testType.parallelism) {
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

        // Add JUnit parallel execution properties
        // https://docs.junit.org/5.3.0-M1/user-guide/index.html#writing-tests-parallel-execution
        systemProperty("junit.jupiter.execution.parallel.enabled", true)
        systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic")
        systemProperty("junit.jupiter.execution.parallel.config.dynamic.factor", 1)
        systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    }
}

/**
 * For KMP projects
 */
fun KotlinJvmTarget.configureTests(maxHeap: String? = null) {
    for (testType in TestType.values()) {
        testRuns.maybeCreate(testType.shortName).executionTask {
            configureCommonTestSettings(testType, maxHeap)
        }
    }
}

/**
 * For pure JVM projects
 */
fun Project.configureJvmTests(maxHeap: String? = null) {
    for (testType in TestType.values()) {
        val shortName = if (testType != TestType.DEFAULT) testType.shortName.uppercaseFirstChar() else ""

        tasks.register<Test>("jvm" + shortName + "Test") {
            configureCommonTestSettings(testType, maxHeap)
        }
    }
}
