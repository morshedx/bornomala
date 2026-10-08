// Top-level build file. The kit's plugin loads AGP (com.android.library and
// com.android.test included), Kotlin with its compose, serialization and JVM
// plugins, KSP, Hilt and Room once, in the root classloader — which is why the
// modules apply those by id, without a version. Only plugins the kit does not
// carry are declared with one here.
plugins {
    id("im.morshed.android.application") apply false
    alias(libs.plugins.play.publisher) apply false
    alias(libs.plugins.androidx.benchmark) apply false
}
