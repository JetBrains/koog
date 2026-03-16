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
    implementation(libs.koog.agents.features.chat.memory.sql)
    implementation(libs.koog.agents.features.persistence.jdbc)
}
