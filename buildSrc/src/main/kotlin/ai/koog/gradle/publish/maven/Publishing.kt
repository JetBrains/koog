package ai.koog.gradle.publish.maven

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import java.net.URL


object Publishing {
    fun Project.publishToMaven() {
        publishTo({
            it.graziePublic(project)
            it.artifactsMaven(project)
        }) {
            it.publications(
                Action {
                    val publications = this

                    publications.forEach {
                        val p = it as MavenPublication
                        p.pom(
                            Action {
                                val pom = this

                                pom.name.set(this@publishToMaven.name)
                                pom.description.set("Koog is a framework for quickly creating AI agents in Kotlin with minimal effort.")
                                pom.url.set("https://github.com/JetBrains/koog")

                                pom.licenses(
                                    Action {
                                        val licenses = this

                                        licenses.license(
                                            Action {
                                                val license = this

                                                license.name.set("The Apache License, Version 2.0")
                                                license.url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                            }
                                        )
                                    }
                                )

                                pom.developers(
                                    Action {
                                        val developers = this

                                        developers.developer(
                                            Action {
                                                val developer = this

                                                developer.id.set("JetBrains")
                                                developer.name.set("JetBrains Team")
                                                developer.organization.set("JetBrains")
                                                developer.organizationUrl.set("https://www.jetbrains.com")
                                            }
                                        )
                                    }
                                )

                                pom.scm(
                                    Action {
                                        val scm = this
                                        scm.url.set("https://github.com/JetBrains/koog.git")
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    private fun Project.publishTo(
        configureRepository: (RepositoryHandler) -> Unit,
        configurePublish: (PublishingExtension) -> Unit = {}
    ) {
        pluginManager.apply("maven-publish")

        extensions.configure<PublishingExtension>("publishing") {
            repositories(Action { configureRepository(this) })
            configurePublish(this)
        }
    }

    private fun RepositoryHandler.artifactsMaven(project: Project) {
        maven(
            Action {
                val repo = this

                repo.name = "artifacts"
                repo.url = project.rootProject.layout.buildDirectory.dir("artifacts/maven").get().asFile.toURI()
            }
        )
    }

    private fun RepositoryHandler.graziePublic(project: Project) {
        maven(
            Action {
                val repo = this

                repo.name = "GraziePublicMaven"
                repo.url = URL("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public").toURI()

                repo.credentials(
                    Action {
                        val cred = this

                        cred.username = project.properties["spaceUsername"]?.toString()
                            ?: System.getenv("JB_SPACE_CLIENT_ID")

                        cred.password = project.properties["spacePassword"]?.toString()
                            ?: System.getenv("JB_SPACE_CLIENT_SECRET")
                    }
                )
            }
        )
    }
}