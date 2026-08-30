plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "koog-agentcore-runtime-example"

// The plugin isn't on Maven Central yet, so consume it from mavenLocal. Publish it once
// from the koog repository root (uses the repo's Gradle wrapper + JDK 17/21):
//
//   ./gradlew :koog-bedrock-agentcore-runtime:publishToMavenLocal
//
// The build.gradle.kts declares mavenLocal() as a repository for this reason.
