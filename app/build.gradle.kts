import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.play.publisher)
}

// App version, reused for the build config and the output APK file name.
val appVersionName = "0.5.29"
val appVersionCode = 36

// Optional release signing config, loaded from a gitignored keystore.properties.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.bornomala.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.morshedx.bornomala"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
