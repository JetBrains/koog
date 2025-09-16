plugins {
    kotlin("jvm") version "2.2.20"
    application
}

application.mainClass.set("ai.koog.example.composite_build_demo.MainKt")

dependencies {
    implementation("ai.koog:koog-agents")
}
