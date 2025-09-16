plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application.mainClass.set("ai.kogi.examples.code.agent.step01.MainKt")

dependencies {
    implementation(libs.koog.all)
}

tasks.test {
    useJUnitPlatform()
}
