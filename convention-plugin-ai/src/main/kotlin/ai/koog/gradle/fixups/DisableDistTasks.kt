package ai.koog.gradle.fixups

import org.gradle.api.Project


object DisableDistTasks {
    private val tasksToDisable = listOf("distZip", "distTar")
    fun Project.disableDistTasks() {
        subprojects {
            gradle.taskGraph.whenReady {
                project.tasks.filter { it.name.split(":").last() in tasksToDisable }.forEach {
                    it.enabled = false
                }
            }
        }
    }
}