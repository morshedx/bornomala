pluginManagement {
    // A local android-kit checkout (-Pkit.dir, else ../android-kit) replaces the
    // published kit, so kit and app change together. Clones without it use maven.
    val kit = providers.gradleProperty("kit.dir").orElse("../android-kit").get()
    if (providers.gradleProperty("kit.local").orNull != "false" && file("$kit/plugins").isDirectory) {
        includeBuild("$kit/plugins")
    }
    repositories {
        maven("https://maven.morshed.im")
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

// Repositories, the `kit` catalog and versions for every other im.morshed.* plugin.
// The app's own catalog stays `libs`.
plugins {
    id("im.morshed.settings") version "2.4.0"
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
