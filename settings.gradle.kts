pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "shift-alarm"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:datastore",
    ":core:alarm",
    ":core:ui",
    ":feature:schedule",
    ":feature:calendar",
    ":feature:alarm",
    ":widget",
)
