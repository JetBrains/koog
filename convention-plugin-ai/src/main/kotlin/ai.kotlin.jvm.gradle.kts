import ai.koog.gradle.tests.configureJvmTests

plugins {
    kotlin("jvm")
    id("ai.kotlin.configuration")
    id("ai.kotlin.dokka")
}

configureJvmTests()
