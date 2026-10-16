pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PRESERVE_EXISTING_REPOSITORIES) // Исправлено здесь
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SamsungToSimpla"
include(":app")
