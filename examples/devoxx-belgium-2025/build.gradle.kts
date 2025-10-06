val koogVersion: String by project

plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.spring") version "2.2.10"
    id("org.springframework.boot") version "4.0.0-M3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ai.koog.sandwich"
version = "0.0.1-SNAPSHOT"
description = "koog-spring-sandwich"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    maven(url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
}


dependencies {
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("ai.koog:koog-spring-boot-starter:$koogVersion")
    implementation("ai.koog:koog-agents:$koogVersion")
    implementation("ai.koog:agents-features-sql:$koogVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    buildInfo()
}
