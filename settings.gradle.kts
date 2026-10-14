pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://teavm.org/maven/repository/")
    }
}
rootProject.name = "hearthvale"
include(":core", ":web", ":android", ":tools", ":desktop")
