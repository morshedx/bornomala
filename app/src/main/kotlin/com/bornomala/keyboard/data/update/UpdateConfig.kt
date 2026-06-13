package com.bornomala.keyboard.data.update

import com.bornomala.keyboard.BuildConfig

/**
 * OTA update source. The app polls [MANIFEST_URL] (a small JSON on Cloudflare R2)
 * and, if it advertises a higher versionCode, downloads the APK it points to.
 *
 * Expected manifest shape (latest.json):
 * ```json
 * {
 *   "versionName": "0.6.0",
 *   "versionCode": 50,
 *   "apkUrl": "https://app-releases.morshed.im/bornomala/bornomala-0.6.0-release.apk",
 *   "notes": "What changed in this release"
 * }
 * ```
 */
object UpdateConfig {
    const val MANIFEST_URL = "https://app-releases.morshed.im/bornomala/latest.json"

    /**
     * Bearer token sent to the R2 gateway Worker. Injected at build time from
     * local.properties (UPDATE_TOKEN) via BuildConfig — never committed.
     */
    val AUTH_TOKEN: String = BuildConfig.UPDATE_TOKEN

    val isConfigured: Boolean get() = AUTH_TOKEN.isNotBlank()
}
