package com.bornomala.keyboard.data.update

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.pm.PackageInfoCompat
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Fetches the OTA manifest and downloads the update APK (no third-party HTTP lib). */
@Singleton
class UpdateService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    fun currentVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }

    fun isDebugBuild(): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer ${UpdateConfig.AUTH_TOKEN}")
        }

    /** Check the remote manifest. */
    suspend fun check(): UpdateStatus = withContext(dispatchers.io) {
        if (!UpdateConfig.isConfigured) {
            return@withContext UpdateStatus.Error("Update source not configured")
        }
        val conn = open(UpdateConfig.MANIFEST_URL)
        try {
            if (conn.responseCode !in 200..299) {
                return@withContext UpdateStatus.Error("Server returned ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val manifest = UpdateManifest.fromJson(body)
            if (manifest.versionCode > currentVersionCode()) {
                UpdateStatus.Available(manifest)
            } else {
                UpdateStatus.UpToDate
            }
        } catch (e: Exception) {
            UpdateStatus.Error(e.message ?: "Update check failed")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Download [url] to cacheDir/updates and return the file. [onProgress] gets a
     * 0f..1f fraction (or -1f when total size is unknown).
     */
    suspend fun download(url: String, onProgress: (Float) -> Unit): File = withContext(dispatchers.io) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Remove any stale APK from a previous download so we never install old bytes.
        dir.listFiles()?.forEach { it.delete() }
        val outFile = File(dir, "bornomala-update.apk")
        val conn = open(url)
        try {
            conn.connect()
            require(conn.responseCode in 200..299) { "Download failed: HTTP ${conn.responseCode}" }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(if (total > 0) downloaded.toFloat() / total else -1f)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        outFile
    }
}
