package ai.koog.gradle.publish.maven

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.attributes

internal fun Project.configureJvmJarManifest(taskName: String) {
    tasks.named(taskName, Jar::class.java) {
        manifest(
            Action {
                attributes(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version,
                    "Automatic-Module-Name" to project.name.replace("-", ".") + ".jvm",
                )
            }
        )
    }
}
