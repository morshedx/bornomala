plugins {
    id("im.morshed.android.application")
    id("im.morshed.android.compose")
    id("im.morshed.android.hilt")
    id("im.morshed.ota-release")
    alias(libs.plugins.play.publisher)
}

// From the kit: compileSdk, Java 17, R8 + resource shrinking with proguard-rules.pro,
// release signing from keystore.properties and the guard that refuses a release
// without it, APK naming (bornomala-<version>-<buildType>.apk), and OTA —
// MANIFEST_URL / UPDATE_TOKEN in BuildConfig, im.morshed:ota, and publishApkToR2,
// which takes its notes from the `## v<version>` section of RELEASE_NOTES.md and
// refuses to publish without one.

// App version.
val appVersionName = "0.10.0"
val appVersionCode = 80

kitApp {
    slug.set("bornomala")
}

android {
    namespace = "com.bornomala.keyboard"

    defaultConfig {
        applicationId = "com.morshedx.bornomala"
        // The app's own target, held where it was before the kit.
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        // Release-like build used by :macrobenchmark for trustworthy cold-start numbers.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            proguardFiles("benchmark-rules.pro")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Gradle Play Publisher — CLI publishing to Google Play.
// Auth: a service-account JSON at the repo root (play-service-account.json, gitignored).
// Create it in Play Console → Users and permissions / API access, grant release perms.
//
// Common tasks:
//   ./gradlew :app:publishReleaseBundle      # upload AAB to the configured track
//   ./gradlew :app:publishReleaseListing     # push store listing text + graphics
//   ./gradlew :app:promoteReleaseArtifact --from-track internal --promote-track production
//
// Metadata (release notes, listing text, graphics) lives in app/src/main/play/.
play {
    val credFile = rootProject.file("play-service-account.json")
    if (credFile.exists()) {
        serviceAccountCredentials.set(credFile)
    }
    defaultToAppBundles.set(true)
    // Safe default: upload to internal testing as a draft so nothing goes live by accident.
    track.set("internal")
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT)
}

dependencies {
    // Feature & shared modules. As feature modules are scaffolded by their owning
    // agents they are wired into the IME and settings host through these deps.
    implementation(project(":core"))
    implementation(project(":theme"))
    implementation(project(":keyboard"))
    implementation(project(":transliteration"))
    implementation(project(":suggestions"))
    implementation(project(":emoji"))
    implementation(project(":clipboard"))
    implementation(project(":settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Cloud backup: Google sign-in/authorization for Drive + background backup scheduling.
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime.ktx)

    // Enables ProfileInstaller so macrobenchmark can measure/compile startup profiles.
    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.hilt.navigation.compose)

    // The tests are JUnit 4; the kit runs every module on the JUnit platform, and
    // without the vintage engine they would be skipped rather than failed.
    testImplementation(libs.junit)
    testRuntimeOnly(kit.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
