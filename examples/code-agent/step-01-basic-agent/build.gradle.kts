plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(libs.koog.all)
}

tasks.test {
    useJUnitPlatform()
}
