plugins {
    // By id, without a version: the kit loads AGP in the root classloader.
    id("com.android.test")
}

android {
    namespace = "com.bornomala.keyboard.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        // A build type matching the app's "benchmark" build type so this test module runs
        // against the release-like, profileable app.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}


kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.junit)
}

androidComponents {
    beforeVariants(selector().all()) {
        // Only the benchmark variant is meaningful for this module.
        it.enable = it.buildType == "benchmark"
    }
}
