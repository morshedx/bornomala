plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.benchmark)
}

android {
    namespace = "com.bornomala.keyboard.microbenchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    // Benchmarks must run against a non-debuggable build for trustworthy numbers.
    testBuildType = "release"

    buildTypes {
        debug {
            // Benchmarks need release-like config; the androidx.benchmark plugin enforces it.
        }
        release {
            isDefault = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    androidTestImplementation(project(":transliteration"))
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
