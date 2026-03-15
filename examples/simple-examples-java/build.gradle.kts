plugins {
    java
    id("ai.koog.gradle.plugins.credentialsresolver")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.koog.agents)
}
