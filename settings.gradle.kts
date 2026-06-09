pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "r2dbc-entity-schema-manager"

include(
    "core",
    "autoconfigure",
    "starter",
)
