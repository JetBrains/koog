import ai.koog.gradle.tests.TestType
import ai.koog.gradle.tests.configureFilter
import org.gradle.kotlin.dsl.support.uppercaseFirstChar

plugins {
    kotlin("jvm")
    id("ai.kotlin.configuration")
    id("ai.kotlin.dokka")
}

for (testType in TestType.values()) {
    val shortName = if (testType != TestType.DEFAULT) testType.shortName.uppercaseFirstChar() else ""
    tasks.register<Test>("jvm" + shortName + "Test") {
        filter { configureFilter(testType) }
        group = "verification"
        testType.maxHeapForJvm?.let {
            maxHeapSize = it
        }
        if (testType.parallelism) {
            maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
