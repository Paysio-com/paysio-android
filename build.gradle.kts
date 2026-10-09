// Paysio Mobile SDK for Android: root build.
//
// Build with: ./gradlew :paysio:assembleRelease  (see README.md)
//
// VGS Collect 2.1.0 sets the floor here: its AAR needs compileSdk 37 (AGP 9+) and it is
// built with Kotlin 2.3, so the Kotlin compiler must be 2.3 or newer to read its metadata.

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
}
