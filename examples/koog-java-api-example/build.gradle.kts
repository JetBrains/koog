val koogVersion: String by project

plugins {
    java
    id("org.springframework.boot") version "4.0.0-SNAPSHOT"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.example"
version = "0.0.1-SNAPSHOT"
description = "koog-java-exp-01"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven(url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("ai.koog:koog-spring-boot-starter:$koogVersion")
    implementation("ai.koog:koog-agents-jvm:$koogVersion")
    implementation("ai.koog:agents-features-sql:$koogVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-teПst-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
