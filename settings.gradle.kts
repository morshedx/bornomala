pluginManagement {
    repositories {
        maven("https://maven.morshed.im") // im.morshed.ota-release plugin + OTA components
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
        maven("https://maven.morshed.im") // im.morshed:ota + shared components
        google()
        mavenCentral()
    }
}

rootProject.name = "Bornomala"

include(":app")
include(":core")
include(":keyboard")
include(":transliteration")
include(":suggestions")
include(":emoji")
include(":clipboard")
include(":settings")
include(":theme")
include(":microbenchmark")
include(":macrobenchmark")
