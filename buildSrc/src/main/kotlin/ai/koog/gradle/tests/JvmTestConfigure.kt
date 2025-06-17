package ai.koog.gradle.tests

import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * @param maxHeap Max RAM for JVM tests
 */
fun KotlinJvmTarget.configureTests(maxHeap: String? = null) {
    for (testType in TestType.values()) {
        testRuns.maybeCreate(testType.shortName).executionTask {
            useJUnitPlatform()
            filter { configureFilter(testType) }
            group = "verification"
            val heapSize = maxHeap ?: testType.maxHeapForJvm
            heapSize?.let { maxHeapSize = it }

            if (testType.parallelism) {
                maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
            }
        }
    }
}
