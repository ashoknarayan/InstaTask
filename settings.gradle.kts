pluginManagement {
    repositories {
        google()
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

rootProject.name = "InstaTask"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:ai",
    ":core:media",
    ":core:worker",
    ":feature:feed",
    ":feature:chat",
    ":feature:vault",
)
