pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
        maven("https://cache-redirector.jetbrains.com/maven-central")
        maven(url = "https://packages.jetbrains.team/maven/p/jcs/maven")
        google()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }

    repositories {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        maven("https://packages.jetbrains.team/maven/p/jcs/maven")
        google()
    }
}
