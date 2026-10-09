package com.paysio.sdk.core

import com.paysio.sdk.BuildConfig

/**
 * Version and platform identifiers the SDK reports to Paysio.
 *
 * Every request carries `X-Paysio-SDK: android/<version>` (CONTRACT.md, intro).
 */
object PaysioSdk {
    /** Semantic version, from `VERSION_NAME` in gradle.properties. */
    val version: String = BuildConfig.VERSION_NAME

    const val platform: String = "android"

    /** `android/0.1.0`, used for the header and `clientMetadata.sdk`. */
    val headerValue: String get() = "$platform/$version"
}
