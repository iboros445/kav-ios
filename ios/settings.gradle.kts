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
rootProject.name = "KavIos"
include(":kav")
// The Swift-facing framework: only the entry point, so Objective-C export covers one function, not all of upstream.
include(":kavkit")
