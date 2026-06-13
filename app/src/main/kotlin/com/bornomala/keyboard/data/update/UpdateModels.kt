package com.bornomala.keyboard.data.update

import org.json.JSONObject

/** The remote OTA manifest (latest.json on R2). Parsed with [JSONObject] — no extra deps. */
data class UpdateManifest(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val notes: String = "",
) {
    companion object {
        fun fromJson(json: String): UpdateManifest {
            val obj = JSONObject(json)
            return UpdateManifest(
                versionName = obj.getString("versionName"),
                versionCode = obj.getLong("versionCode"),
                apkUrl = obj.getString("apkUrl"),
                notes = obj.optString("notes", ""),
            )
        }
    }
}

/** Result of an update check. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val manifest: UpdateManifest) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}
