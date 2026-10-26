pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

val localMavenValidation =
    providers.gradleProperty("project.repositories.mavenLocal")
        .map { it.trim().toBooleanStrict() }
        .getOrElse(false)

dependencyResolutionManagement {
    if (localMavenValidation) {
        val localMavenPath = providers.systemProperty("maven.repo.local").orNull
        require(localMavenPath != null && File(localMavenPath).isAbsolute) {
            "Local Maven validation requires -Dmaven.repo.local=/absolute/path/to/isolated-repository"
        }
        // Kotlin tool setup temporarily adds and removes its Node/Yarn Ivy repositories.
        repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    }
    repositories {
        if (localMavenValidation) {
            exclusiveContent {
                forRepository {
                    mavenLocal { name = "kotlinManiaLocalValidation" }
                }
                filter { includeGroup("io.github.kotlinmania") }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "thiserror-kotlin"
