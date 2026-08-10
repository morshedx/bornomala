import java.util.Properties

// Secrets (e.g. OTA gateway token) come from the environment first, then
// local.properties as a fallback. Never committed.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val updateToken: String =
    System.getenv("UPDATE_TOKEN") ?: localProps.getProperty("UPDATE_TOKEN") ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.play.publisher)
    // OTA self-update release task (./gradlew publishApkToR2): builds the signed APK, writes
    // latest.json, and uploads both to the Cloudflare R2 bucket the app polls for updates.
    id("im.morshed.ota-release") version "1.3.0"
}

// App version, reused for the build config and the output APK file name.
val appVersionName = "0.8.8"
val appVersionCode = 63

// Never ship a debug-signed release: the OTA library installs updates in place, and a signing
// signature flip on the next release would fail the in-place update and wipe user data. Fail
// loudly if a release/publish task runs without the stable release keystore.
gradle.taskGraph.whenReady {
    val needsRelease = allTasks.any {
        val n = it.name
        (n.contains("Release") && (n.startsWith("assemble") || n.startsWith("bundle") || n.startsWith("package"))) ||
            n.contains("publishApkToR2", ignoreCase = true)
    }
    if (needsRelease && !rootProject.file("keystore.properties").exists()) {
        throw GradleException(
            "Release signing key missing: keystore.properties not found. Refusing to build/publish " +
                "a release — a debug-signed release would break OTA updates and wipe user data.",
        )
    }
}

// Optional release signing config, loaded from a gitignored keystore.properties.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.bornomala.keyboard"
    compileSdk = 35

    defaultConfig {
        // minSdk 29: required by the im.morshed:ota self-update library (drops Android 8.0–9).
        applicationId = "com.morshedx.bornomala"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // OTA gateway bearer token, injected from env / local.properties (never committed).
        buildConfigField("String", "UPDATE_TOKEN", "\"$updateToken\"")
        // OTA version manifest the app polls for newer releases (Cloudflare R2, bornomala slug).
        buildConfigField("String", "MANIFEST_URL", "\"https://app-releases.morshed.im/bornomala/latest.json\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Name the output APKs with the version, e.g. bornomala-1.0.0-release.apk.
base {
    archivesName.set("bornomala-$appVersionName")
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

// OTA release task (im.morshed.ota-release): ./gradlew publishApkToR2 builds the signed release
// APK, writes the version manifest, and uploads both to the R2 bucket the app polls (latest.json
// at <baseUrl>/<appSlug>/). R2 credentials come from the environment, never committed.
otaRelease {
    bucket.set("app-releases")
    baseUrl.set("https://app-releases.morshed.im")
    appSlug.set("bornomala")
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    // Cloud backup: Google sign-in/authorization for Drive + background backup scheduling.
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime.ktx)

    // OTA self-update: version check, download, and PackageInstaller flow + the UpdateScreen UI.
    // Self-contained (own Hilt ViewModel, worker, InstallReceiver, FileProvider via manifest merge);
    // configured by OtaModule (manifest URL + bearer token from BuildConfig).
    implementation("im.morshed:ota:1.3.0")

    // Enables ProfileInstaller so macrobenchmark can measure/compile startup profiles.
    implementation(libs.androidx.profileinstaller)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
